# Gateway test transport

The image pipeline runs the full gateway suite and footprint mutation campaign on es4.
The Package stage uses `-DskipTests`: a source/artifact/build-bound test receipt is required
first, so the large tests never run again during native packaging. Application hot paths
and packaging/image/deployment source guards are unchanged.

This intentionally prevents image builds during 08:30–16:00 America/New_York on weekdays,
including cash-hours hotfix builds. There is no skip/override. The 60-minute pre-open margin
reserves 20 minutes beyond the 40-minute test deadline for cleanup. es4 is the test host named by the
Performance Rule; no Jenkins controller or agent is installed there. A successful real
Jenkins sequence, with its measured duration and reports, is required before a production
image rollout. Forty minutes is an initial fail-closed ceiling, not a duration benchmark.
The actual duration has not yet been measured on es4; the first reviewed-main build must
establish it, and failure cannot authorize packaging or deployment.

`test-on-es4.sh` requires Jenkins, full permitted source/contract SHAs and a numeric build
number. It reruns the main-tip guard/tree check, checks the exact installed contracts JAR
and POM pin, and sends a checksummed Git bundle plus this build's private Maven cache to
`abhinav@192.168.100.4`. This existing SSH account is the trusted test executor. es4 verifies
the exact commit/tree, not an independent live GitHub tip; the Jenkins checkout makes the
live-tip check. The snapshot includes commit history and works without remote GitHub
credentials. The selected Jenkins agent must already have this SSH route and host key;
StrictHostKeyChecking refuses missing provisioning. No key or host-toolchain change occurs.

Before upload, es4 must provide Java 21 at `/usr/lib/jvm/java-21-openjdk`, Maven, Python,
Git, Docker on its default socket, a reachable user-systemd manager (running or degraded), New York timezone data,
and 40 GiB free disk. The remote rechecks the market window before test execution. Maven
may resolve Linux dependencies/plugins independently from native packaging, including the
host Maven settings: only the exact contracts JAR is source/hash-bound here. The shipped JAR
is packaged separately from the same source on Jenkins, rather than being the tested binary.
Java and Maven versions are emitted inside the unit into the hashed log. Dependencies and
plugins are neither all preinstalled nor independently pinned by this transport.
The Mac cache tar suppresses AppleDouble files; GNU tar can still warn about ignored
Apple provenance PAX attributes. Artifact content hashes remain verified. A 4 GiB cache cap fails loudly; cached
coordinates remain per Jenkins workspace, and a full cache requires deliberate maintenance
through Jenkins rather than silently evicting the bound contracts dependency.

Before upload, a short unit probe using the same clean environment reads back effective
kernel memory/swap/pid limits and inherited two-core affinity and checks Docker access. Missing memory/pid delegation or
unusable Docker fails before the cache transfer. The command runs in a unique transient user-systemd service with RuntimeMaxSec=2400,
TimeoutStopSec=10, KillMode=control-group, MemoryMax=4 GiB, no swap, 512 tasks and an inherited two-core CPU affinity via `taskset`. CPU cgroup delegation
is unavailable on the inspected host, so no CPU quota is claimed. systemd stops every descendant even if the leader exits or TERM is ignored.
The isolated Kafka test container is limited separately to 2 GiB, no swap and one CPU.
The planned total budget is 8 GiB including the service, Kafka, Ryuk and executor/native
headroom. These are CI test bounds, not a measured throughput or capacity qualification.
Cleanup stops only a unit whose name and ExecStart belong to this private run directory.
A remote EXIT/signal trap also stops the service; RuntimeMaxSec bounds a lost connection.

The unique workspace root refuses four retained directories or 20 GiB existing data.
During execution a ten-second watchdog stops the run if total private-root usage exceeds
20 GiB; growth can overshoot the threshold during that interval. A single log is limited to
128 MiB, each report to 32 MiB and total XML to 128 MiB/512 files. Cleanup removes only this
run's files without following symlinks, under a two-minute remote deletion deadline. Local cache/source snapshots are removed in the
transport trap. Jenkins archives bounded evidence, then removes the local evidence directory
in the enclosing Build stage cleanup, after the receipt gate and packaging have used it.

The source snapshot runs `mvn -B test`, `scripts/footprint-reverify.sh`, and the rendered
requirement-state check. Full-suite Surefire reports are copied before mutation runs can
overwrite them. A receipt requires positive executed-test count, no failures/errors and
hashes of every XML report plus the log. The sequence writes a source-bound completion
marker only after every test/campaign/render command succeeds; both supervision and receipt
verification require it, independently of systemd exit status. The packaging gate recomputes those hashes and
checks source SHA, contracts SHA/JAR and build number. Missing reports or failed transfer
cannot become a pass. es4 is the trust root for the receipt; the receipt proves which
reported evidence was accepted, not independent attestation against a compromised executor.

Validation (small tests only; SSH/SCP/systemd/Maven are stubbed in transport fixtures):

```
python3 scripts/jenkins/test-on-es4-test.py
python3 scripts/jenkins/es4-test-receipt-test.py
bash scripts/jenkins/validate-jenkinsfile-guard.sh Jenkinsfile
bash -n scripts/jenkins/test-on-es4.sh
```

The fixtures exercise the real bundle/checksum/cache/receipt flow with stubbed guard bodies
and the Linux CPU-affinity syscall replaced in the Mac fixture.
The existing guard validator separately tests those real guards. Transport tests cover source,
artifact and pin refusal, failed command, zero-status incomplete sequence, window, prior-attempt refusal and unit ownership.
Receipt faults cover missing/empty/failed/all-skipped/altered evidence and source mismatch.
They contact no host, broker or Docker daemon and do not prove real systemd/container limits.

Failure evidence is collected independently of success-only files: `test.log`,
`receipt.json` and `complete.txt` each use their own SCP operation. A missing marker
or receipt cannot suppress the log transfer. Bounded full-suite XML is preserved
on Maven failure as well as success, before any mutation can overwrite it. A
regression fixture models SFTP pre-validation of all sources and demonstrates the
old grouped-copy failure alongside the successful independent-copy behavior.

## Full-suite class isolation

The first real run hit the unit's 512-task limit. Its kernel log records the
rejected fork; a subsequent thread dump contained 214 `options-edge-ws-writer`
threads left from earlier test classes before a later embedded-web-server class
started. The isolated process had sufficient memory (about 1.4 GiB at the task
limit); increasing memory would not fix this failure.

The remote Maven environment now supplies `-DreuseForks=false` to the full suite
and nested footprint Maven invocations. Surefire releases each class's JVM and its
idle thread pools before the next class. All tests and campaign commands remain,
and the 512-task/4 GiB limits remain enforced. Existing smoke-test fork/environment
settings remain. Native packaging does not inherit this remote-only property.

This Jenkins installation has no `junit` Pipeline step. Logs, raw XML, completion
marker and receipt are archived directly with `archiveArtifacts`; the existing
Python receipt gate still validates test counts, errors, hashes and provenance.
An unavailable results-publisher plugin can no longer prevent diagnostic archival.

Validation: the Maven property evaluator resolves `reuseForks` to `false` when
supplied through MAVEN_OPTS. Transport fixtures require the property; a negative
control removes it and cannot create a passing receipt. A fresh real Jenkins run
still must qualify the full suite and campaign under the unchanged limits.

## The contracts version every maven resolves

The transport installs contracts from main's tip, so the version it carries is not the version this
pom pins, and the pin goes stale on every contracts release. `run-tests.sh` names the installed
version on the full suite's own `mvn` command line. The footprint campaign's `mvn` cannot be told
that way: its argv comes from the committed `scripts/footprint-campaign.spec.json` and is part of
the claim each row of `ES-FOOTPRINT-CAMPAIGN.json` makes, so it carries no per-build version. Until
build #826 nothing supplied one, and the campaign's BASELINE resolved the pin, missed it in this
build's private repository, went to the network and reported `BASELINE IS RED` before a single
mutation ran — with nothing in the console log, because run-tests.sh writes to `test.log`.

So the version is supplied as environment, beside the repository that holds the jar: the transport
writes `.mvn/maven.config` in the RUN ROOT, which maven finds by walking up from the project
directory, so the verified checkout is untouched. The two mechanisms that look obvious do not work
and were measured, not argued: a `-D` in `MAVEN_OPTS` is a JVM system property and LOSES to a pom
property, and `MAVEN_ARGS` needs maven 3.9 where es4 has 3.6.3.

Validation: the fixture runs the campaign's unqualified `mvn` exactly as the spec does, and the stub
resolves the version the way maven does — command line, then the nearest `.mvn/maven.config` walking
up, then the pom — and refuses any version that is not an artifact in the run's own repository. With
the run-root write removed, three of the eighteen cases go red on the message build #825 died on.

## Campaign evidence capture

Run 739 passed all 1,403 full-suite tests and ran all 50 mutations (49 KILLED,
one known SURVIVED). Comparison refused an additional assertion witness for G-R10.10.
The committed record also predates the CVD reader source changes, so its whole-file
source hash is stale. Neither comparison nor source-hash validation is weakened.

A full-gate invocation now saves its actual merged record as
`target/footprint-reverify.json` before comparison, including on a comparison failure.
The record is capped at 8 MiB, copied in 64 KiB blocks and published through a temporary
file and atomic replace. Prior evidence is removed at entry, partial manual runs do
not publish gate evidence, and a symlinked target directory is refused. Transport
copies the record independently before remote cleanup; Jenkins archives it alongside
the existing failure evidence. It is diagnostic evidence, not a successful receipt.

The first preparation run is expected to stop at the existing stale-record gates.
Request no image push for that run. Review the actual Jenkins-produced record, its
baseline and source hashes; commit that evidence and regenerate the design document
before the final image qualification. Never replace source hashes or baseline output
with invented values. All runtime source writers remain gated.

Small capture fixtures exercise success, failed comparison, malformed input, stale
prior artifacts, partial runs, symlink refusal and the size bound. Negative controls
remove capture or raise the limit, and the corresponding assertions then fail.
The transport fixtures remain stubbed and do not qualify a real host or campaign.


## Current record and deterministic heap-failure setup

Preparation run741 (gateway sourceee65b163f51f00d64c1d91e623522dbda5576330,
contracts0.9.0) passed all1,403 full-suite tests and ran all50 mutations:
49 KILLED and the same one known SURVIVED. Every recorded claim/outcome reproduced.
It stopped at the expected stale FeedGatewayService source-hash check. No image
was pushed or runtime deployed. Its complete archived footprint-reverify.json is
copied byte-for-byte into ES-FOOTPRINT-CAMPAIGN.json; no baseline, hash, output tail
or failure name is edited. The renderer requires one campaign commit, so all50 rows
come from this one run. Regenerating/checking the conformance section leaves it
unchanged because its requirement/outcome claims are unchanged.

Run739 observed an extra assertion failure in the general pool/report heap-failure
test under the hand-over mutation; run741 did not. The dedicated next test already
forces the close-before-hand-over-return race deterministically. The general heap
case now queues its concurrent pool tasks behind a bounded latch until the overflow
fan-out returns, separating that pool/report failure scenario from the dedicated
race scenario. All existing heap/thread/report/fan-out/retry assertions remain.
Cleanup releases the latch on every exit. Neither comparator nor mutation specs,
assertion requirements, resource caps or receipt gates are weakened.

The gateway now pins the reviewed contracts0.10.0 artifact, built by Jenkins80 from
contracts main4b0376131f00c0d4297fb45f0584010f6c2227d8 (837 tests). Its coreCVD
schemas are unchanged. The new whole-volume regime schema and writer cache do not
add a gateway writer. Changed test compilation is local only; real stress/test and
all50-mutation execution remain on es4 through the final Jenkins image qualification.
This preparation does not itself authorize a source writer or claim a production rollout.
