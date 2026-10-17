# Gateway test transport

The image pipeline runs its full gateway suite and footprint mutation campaign on es4,
outside 08:45–16:00 America/New_York on weekdays. The exclusion includes a 45-minute
pre-open margin for the 40-minute test deadline. Packaging, image publication and deployment
retain the original Jenkins workspace and guards.

`test-on-es4.sh` requires Jenkins, full permitted source/contract SHAs and a numeric build
number. It reruns the source guard/tree check, checks the installed contracts JAR hash and
POM pin, and sends a checksummed Git bundle plus this build's private Maven cache to a unique
workspace on `abhinav@192.168.100.4`. The host already has Java 21 at
`/usr/lib/jvm/java-21-openjdk`, Maven and Docker. No host-wide toolchain or GitHub credential
change is made. The test copy checks the exact Git commit/tree and contracts artifact; it
does not claim to perform an independent live GitHub-tip check. The original Jenkins
checkout performs that check before transport.

A successful receipt binds the gateway SHA, contracts SHA, contracts JAR hash and build
number. The following stage requires that receipt before packaging. Reports/logs are
collected even after failure. A test or transport failure never becomes a passing receipt.
The source snapshot runs `mvn -B test`, `scripts/footprint-reverify.sh`, then the rendered
requirement-state check, with no test omitted from the former native stages.

The test command has a 40-minute process-group timeout inside a 45-minute Jenkins stage.
Cleanup targets only its recorded process group after checking its command line and group
identity, then removes that run's private files without following symlinks. Failed cleanup
is bounded by a refusal at four retained workspaces or 20 GiB of previous workspace data.
The private Maven cache is limited to 4 GiB. The planned host-memory budget is 8 GiB for
Surefire, Maven, native headroom and isolated Testcontainers resources. No throughput or
peak-memory qualification is claimed by the transport unit tests.

Validation:

```
python3 scripts/jenkins/test-on-es4-test.py
bash scripts/jenkins/validate-jenkinsfile-guard.sh Jenkinsfile
bash -n scripts/jenkins/test-on-es4.sh
```

The seven small tests stub SSH/SCP/Maven, replacing only host-specific filesystem/JDK
literals in a test copy. They exercise source/artifact/pin integrity, command failure,
receipt binding/report collection, cash-window refusal and prior-attempt refusal. They
contact no host, broker or Docker daemon. Actual es4 execution and its reports must be
verified from the Jenkins image build before a production rollout.
