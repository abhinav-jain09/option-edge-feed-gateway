# Gateway test transport

The image pipeline runs the full gateway suite and footprint mutation campaign on es4.
The Package stage uses `-DskipTests`: a source/artifact/build-bound test receipt is required
first, so the large tests never run again during native packaging. Application hot paths
and packaging/image/deployment source guards are unchanged.

This intentionally prevents image builds during 08:45–16:00 America/New_York on weekdays,
including cash-hours hotfix builds. There is no skip/override. The 45-minute pre-open margin
covers the initial 40-minute test deadline plus cleanup. es4 is the test host named by the
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
Git, Docker on its default socket, a running user-systemd manager, New York timezone data,
and 40 GiB free disk. The remote rechecks the market window before test execution. Maven
may resolve Linux dependencies/plugins from its configured upstream repositories: only the
exact contracts JAR is source/hash-bound here, matching the prior native dependency trust.
No claim is made that all Maven dependencies are preinstalled or independently pinned.
The Mac cache tar suppresses Apple xattr metadata. A 4 GiB cache cap fails loudly; cached
coordinates remain per Jenkins workspace, and a full cache requires deliberate maintenance
through Jenkins rather than silently evicting the bound contracts dependency.

The command runs in a unique transient user-systemd service with RuntimeMaxSec=2400,
TimeoutStopSec=10, KillMode=control-group, MemoryMax=4 GiB, no swap, 512 tasks and two CPU
cores. systemd stops every descendant even if the leader exits or TERM is ignored.
The isolated Kafka test container is limited separately to 2 GiB, no swap and one CPU.
The planned total budget is 8 GiB including the service, Kafka, Ryuk and executor/native
headroom. These are CI test bounds, not a measured throughput or capacity qualification.
Cleanup stops only a unit whose name and ExecStart belong to this private run directory.
A remote EXIT/signal trap also stops the service; RuntimeMaxSec bounds a lost connection.

The unique workspace root refuses four retained directories or 20 GiB existing data.
During execution a ten-second watchdog stops the run if total private-root usage exceeds
20 GiB; growth can overshoot the threshold during that interval. A single log is limited to
128 MiB, each report to 32 MiB and total XML to 128 MiB/512 files. Cleanup removes only this
run's files without following symlinks. Local cache/source snapshots are removed in the
transport trap. Jenkins archives bounded evidence, then removes the local evidence directory
in the enclosing Build stage cleanup, after the receipt gate and packaging have used it.

The source snapshot runs `mvn -B test`, `scripts/footprint-reverify.sh`, and the rendered
requirement-state check. Full-suite Surefire reports are copied before mutation runs can
overwrite them. A receipt requires positive executed-test count, no failures/errors and
hashes of every XML report plus the log. The packaging gate recomputes those hashes and
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

The fixtures exercise the real bundle/checksum/cache/receipt flow with stubbed guard bodies.
The existing guard validator separately tests those real guards. Transport tests cover source,
artifact and pin refusal, failed command, window, prior-attempt refusal and unit ownership.
Receipt faults cover missing/empty/failed/all-skipped/altered evidence and source mismatch.
They contact no host, broker or Docker daemon and do not prove real systemd/container limits.
