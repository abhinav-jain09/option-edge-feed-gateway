"""Finite transport faults, with SSH/SCP/Maven stubbed; no host, broker or large suite is contacted."""
import hashlib
import os
import re
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SOURCE = Path(__file__).with_name("test-on-es4.sh")


class TransportTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="gateway-ci-test-", dir="/tmp")
        self.root = Path(self.tmp.name)
        self.repo = self.root / "repo"
        self.bin = self.root / "bin"
        self.jdk = self.root / "jdk"
        self.remote = self.root / "remote"
        self.repo.mkdir()
        self.bin.mkdir()
        (self.jdk / "bin").mkdir(parents=True)
        self.write(self.repo / "pom.xml", '<project xmlns="http://maven.apache.org/POM/4.0.0"><properties><options-edge-contracts.version>0.8.0</options-edge-contracts.version></properties></project>')
        self.write(self.repo / ".gitignore", ".m2/\n.jenkins-tmp/\n.contracts-*\ntarget/\n")
        self.script(self.repo / "scripts/jenkins/permitted-sha-guard.sh", 'test "$(git rev-parse HEAD)" = "$PERMITTED_SHA"')
        self.script(self.repo / "scripts/jenkins/verify-permitted-tree.sh", '''
dir=.
while [ $# -gt 0 ]; do case "$1" in --dir) dir=$2;shift 2;; --allow-ignored) shift 2;; *) exit 64;; esac;done
test "$(git -C "$dir" rev-parse HEAD)" = "$PERMITTED_SHA"
''')
        self.script(self.repo / "scripts/jenkins/permitted-sha-guard-version.sh", 'shasum -a 256 "$1" | cut -d" " -f1')
        # The campaign's maven is the one that broke: scripts/footprint-campaign.spec.json names
        # `mvn -B test -Dtest=Footprint*,...` and NOTHING else, because that argv is part of the
        # committed campaign record. The fixture runs it the same way, so the stub below is asked
        # the real question — what does a maven with no version of its own resolve?
        self.script(self.repo / "scripts/footprint-reverify.sh",
                    'echo footprint-check\nmvn -B test -Dtest=Footprint*,CvdSpxLevelsWiringTest -DfailIfNoTests=false')
        self.script(self.repo / "scripts/footprint-reqstate.sh", 'echo footprint-check')
        self.script(self.jdk / "bin/java", 'echo \'openjdk version "21"\' >&2')
        self.maven(0)
        self.script(self.bin / "date", 'printf "%s\\n" "${CI_FAKE_DATE:-6 0700 EDT}"')
        self.script(self.bin / "taskset", 'if [ "$1" = -pc ]; then echo "affinity: 0,1"; else shift 2; exec "$@"; fi')
        self.script(self.jdk / "bin/taskset", 'echo "affinity: 0,1"')
        self.script(self.bin / "docker", 'echo finite-docker-preflight')
        self.script(self.bin / "df", 'printf "Filesystem 1024-blocks Used Available Capacity Mounted\\nfixture 999999999 0 999999999 0%% /\\n"')
        self.script(self.bin / "systemctl", 'case "$*" in *is-system-running*) echo "${CI_MANAGER_STATE:-running}"; [ "${CI_MANAGER_STATE:-running}" = running ];; *show*) cat "${CI_UNIT_EXEC}" 2>/dev/null || true;; *stop*) printf "%s\\n" "$*" >> "$CI_UNIT_STOPS";; esac')
        self.script(self.bin / "systemd-run", '''
# The kernel probe is not a host operation in this fixture; the final command is exercised.
case "$*" in *oe-gateway-probe-*) exit 0;; esac
# The properties are the only place the run bound is observable, and the shift loop below drops
# them; record the real run's argv before it does.
printf "%s\n" "$*" >> "$CI_UNIT_ARGV"
while [[ $1 = --* ]]; do shift; done
if [ "${CI_FAKE_PARTIAL:-}" = 1 ]; then
    last="${@: -1}"
    sed '/complete.txt/d' "$last" > "$last.partial"
    set -- "${@:1:$(($# - 1))}" "$last.partial"
fi
exec "$@"
''')
        self.write(self.repo / "scripts/jenkins/es4-test-receipt.py", SOURCE.with_name("es4-test-receipt.py").read_text())
        self.script(self.bin / "sha256sum", 'exec shasum -a 256 "$@"')
        self.write(self.bin / "setsid", "#!/usr/bin/env python3\nimport os,sys\nos.setsid()\nos.execvp(sys.argv[1],sys.argv[1:])\n", True)
        self.script(self.bin / "timeout", 'shift;exec "$@"')
        self.write(self.bin / "ssh", '''#!/usr/bin/env python3
import os,sys
args=sys.argv[1:]
while args and args[0]=='-o':args=args[2:]
assert args.pop(0)=='abhinav@192.168.100.4'
with open(os.environ['CI_CALLS'],'a') as f:f.write('ssh\\n')
os.execvp('bash',['bash','-c',' '.join(args)])
''', True)
        self.write(self.bin / "scp", '''#!/usr/bin/env python3
import os,pathlib,shutil,sys
args=sys.argv[1:];recursive=False
while args and args[0].startswith('-'):
 if args[0]=='-o':args=args[2:]
 elif args[0]=='-r':recursive=True;args=args[1:]
 else:raise ValueError(args[0])
def local(s):return s.removeprefix('abhinav@192.168.100.4:')
dest=pathlib.Path(local(args[-1]))
# OpenSSH SFTP may validate every source before copying any of them.
for source in args[:-1]:
 if not pathlib.Path(local(source)).exists():raise FileNotFoundError(source)
for source in args[:-1]:
 p=pathlib.Path(local(source));target=dest/p.name if dest.is_dir() else dest
 if p.is_dir():shutil.copytree(p,target,dirs_exist_ok=True)
 else:shutil.copy2(p,target)
''', True)
        # The fixture replaces host filesystem/JDK literals; real guard commands are separately stubbed above.
        self.runner = self.root / "transport.sh"
        self.write(self.runner, SOURCE.read_text().replace("/home/abhinav/ci/gateway-tests", str(self.remote))
                   .replace("/usr/lib/jvm/java-21-openjdk", str(self.jdk)).replace("os.sched_getaffinity(0)", "range(2)"), True)
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.name", "CI unit fixture")
        self.git("config", "user.email", "fixture@example.invalid")
        self.git("add", ".")
        self.git("commit", "-qm", "fixture")
        self.sha = self.git("rev-parse", "HEAD").strip()
        self.jar = self.repo / ".m2/repository/com/optionsedge/options-edge-contracts/0.8.0/options-edge-contracts-0.8.0.jar"
        self.write(self.jar, "fixed contracts fixture")
        self.write(self.repo / ".contracts-version", "0.8.0\n")
        self.write(self.repo / ".contracts-sha", "c" * 40 + "\n")
        self.write(self.repo / ".contracts-jar-sha256", hashlib.sha256(self.jar.read_bytes()).hexdigest() + "\n")
        self.env = {**os.environ, "PATH": str(self.bin) + os.pathsep + os.environ["PATH"],
                    "JENKINS_URL": "unit-fixture", "PERMITTED_SHA": self.sha,
                    "CONTRACTS_PERMITTED_SHA": "c" * 40, "BUILD_NUMBER": "123",
                    "CI_CALLS": str(self.root / "calls"), "CI_FAKE_DATE": "6 0700 EDT",
                    # Pinned, never inherited. Jenkins exports every build parameter into the
                    # environment, so under the real pipeline os.environ already carries the waiver
                    # the build was triggered with — and build 811 proved it: the default-path test
                    # ran the whole transport on a waiver no test had asked for. Each test that cares
                    # sets this itself; every other test must see the shipped default.
                    "ALLOW_CASH_WINDOW": "false",
                    "CI_UNIT_EXEC": str(self.root / "unit-exec"), "CI_UNIT_STOPS": str(self.root / "unit-stops"),
                    "CI_UNIT_ARGV": str(self.root / "unit-argv")}

    def tearDown(self):
        self.tmp.cleanup()

    def write(self, path, body, executable=False):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body)
        if executable:
            path.chmod(0o755)

    def script(self, path, body):
        self.write(path, "#!/usr/bin/env bash\nset -euo pipefail\n" + body + "\n", True)

    def git(self, *args):
        return subprocess.check_output(["git", "-C", str(self.repo), *args], text=True)

    def maven(self, result):
        self.script(self.jdk / "bin/mvn", '''
if [ "$*" = -version ]; then echo "Apache Maven fixture"; exit 0; fi
[[ ${MAVEN_OPTS:-} = *-DreuseForks=false* ]] || { echo "Missing test-class JVM isolation"; exit 4; }
# The remote test MUST be told the contracts version the build installed. Without this the mvn
# here falls back to the pom's pin, which goes stale the moment contracts main bumps — the exact
# failure this assertion exists to make impossible to reintroduce silently.
[[ ${CI_CONTRACTS_VERSION:-} = 0.8.0 ]] || { echo "CI_CONTRACTS_VERSION not passed through env -i"; exit 6; }
# TWO invocations with different obligations. The full suite is run by run-tests.sh, which names the
# installed version itself — that is asserted outright. The campaign's comes from the committed spec
# and names nothing, so it is resolved the way maven resolves it: a -D on the command line wins,
# else the nearest .mvn/maven.config walking UP from the project directory, else the pom's own
# property. Whatever that yields must be an artifact in this run's private repository, or this stub
# fails with the message real maven gave in build #825.
if [[ "$*" = *-Dtest=Footprint* ]]; then
    version=""
    for arg in "$@"; do case "$arg" in -Doptions-edge-contracts.version=*) version=${arg#*=};; esac; done
    if [ -z "$version" ]; then
        dir=$PWD
        while [ "$dir" != / ]; do
            if [ -f "$dir/.mvn/maven.config" ]; then
                version=$(sed -n 's/^-Doptions-edge-contracts[.]version=//p' "$dir/.mvn/maven.config" | tail -1)
                break
            fi
            dir=$(dirname "$dir")
        done
    fi
    [ -n "$version" ] || version=$(tr -d ' \t\n' < pom.xml | sed -e 's|.*<options-edge-contracts[.]version>||' -e 's|<.*||')
    repository=${MAVEN_OPTS#*-Dmaven.repo.local=}; repository=${repository%% *}
    [ -f "$repository/com/optionsedge/options-edge-contracts/$version/options-edge-contracts-$version.jar" ] || {
        echo "[ERROR] Could not resolve dependencies: Could not find artifact com.optionsedge:options-edge-contracts:jar:$version"
        exit 1; }
else
    [[ "$*" = *-Doptions-edge-contracts.version=0.8.0* ]] || { echo "Missing installed contracts version override: $*"; exit 5; }
fi
mkdir -p "$CI_GATEWAY_DIR/target/surefire-reports"
printf '<testsuite tests="1" failures="0"/>' > "$CI_GATEWAY_DIR/target/surefire-reports/TEST-fixture.xml"
echo finite-maven-stub
exit %d
''' % result)

    def run_transport(self, expected=0):
        result = subprocess.run(["bash", str(self.runner)], cwd=self.repo, env=self.env,
                                text=True, capture_output=True, timeout=30)
        self.assertEqual(expected, result.returncode, result.stdout[-2000:] + result.stderr[-2000:])
        return result

    def test_success_binds_source_jar_and_build_and_collects_reports(self):
        self.run_transport()
        out = self.repo / ".jenkins-tmp/es4-tests-123"
        import json
        receipt = json.loads((out / "receipt.json").read_text())
        self.assertEqual(self.sha, receipt["source"])
        self.assertEqual("c" * 40, receipt["contracts_source"])
        self.assertEqual("123", receipt["build_number"])
        self.assertEqual("PASS", receipt["status"])
        self.assertEqual(1, receipt["tests"])
        self.assertFalse((out / "source.bundle").exists())
        self.assertFalse((out / "maven-cache.tar.gz").exists())
        self.assertTrue((out / "surefire-reports/TEST-fixture.xml").is_file())
        self.assertEqual([], list(self.remote.iterdir()))

    def test_maven_failure_cannot_publish_a_pass_receipt(self):
        self.maven(2)
        self.run_transport(1)
        out = self.repo / ".jenkins-tmp/es4-tests-123"
        self.assertFalse((out / "receipt.json").exists())
        self.assertIn("finite-maven-stub", (out / "test.log").read_text())
        self.assertTrue((out / "surefire-reports/TEST-fixture.xml").is_file())
        self.assertEqual([], list(self.remote.iterdir()))

    def test_grouped_optional_sources_reproduce_the_missing_failure_log(self):
        body = self.runner.read_text()
        start = body.index("  for evidence in test.log receipt.json complete.txt;")
        end = body.index("\n  done", start) + len("\n  done")
        body = body[:start] + '  scp "${ssh_args[@]}" "$host:$remote_dir/test.log" "$host:$remote_dir/receipt.json" "$host:$remote_dir/complete.txt" "$local_dir/" >/dev/null 2>&1 || true' + body[end:]
        self.runner.write_text(body)
        self.maven(2)
        self.run_transport(1)
        self.assertFalse((self.repo / ".jenkins-tmp/es4-tests-123/test.log").exists())

    def test_zero_status_without_sequence_completion_cannot_pass(self):
        self.env["CI_FAKE_PARTIAL"] = "1"
        self.run_transport(1)
        out = self.repo / ".jenkins-tmp/es4-tests-123"
        self.assertTrue((out / "surefire-reports/TEST-fixture.xml").is_file())
        self.assertFalse((out / "receipt.json").exists())

    def test_degraded_manager_is_reachable_even_with_nonzero_exit(self):
        self.env["CI_MANAGER_STATE"] = "degraded"
        self.run_transport()

    def test_stopping_manager_refuses_before_cache_upload(self):
        self.env["CI_MANAGER_STATE"] = "stopping"
        self.run_transport(1)
        self.assertFalse((self.repo / ".jenkins-tmp/es4-tests-123/receipt.json").exists())

    def test_missing_fork_isolation_is_detected_before_success(self):
        body = self.runner.read_text()
        self.assertIn("-DreuseForks=false", body)
        self.runner.write_text(body.replace("-DreuseForks=false ", ""))
        self.run_transport(1)
        out = self.repo / ".jenkins-tmp/es4-tests-123"
        self.assertIn("Missing test-class JVM isolation", (out / "test.log").read_text())
        self.assertFalse((out / "receipt.json").exists())

    def test_wrong_artifact_or_source_fails_before_any_transport(self):
        self.jar.write_text("altered artifact")
        self.run_transport(1)
        self.assertFalse((self.root / "calls").exists())

    def test_wrong_source_refuses_before_any_transport(self):
        self.env["PERMITTED_SHA"] = "f" * 40
        self.run_transport(1)
        self.assertFalse((self.root / "calls").exists())

    def stale_pom_pin(self):
        """The real condition: contracts main bumped, so the installed version (.contracts-version,
        0.8.0 here) is NOT the version pinned in this pom (0.9.0). The build must proceed and must
        resolve the version it installed. Requiring the two to agree is what forced a pin-chasing
        commit on every contracts release."""
        p = self.repo / "pom.xml"
        p.write_text(p.read_text().replace("0.8.0", "0.9.0"))
        self.git("add", "pom.xml")
        self.git("commit", "-qm", "stale pom pin fixture")
        self.env["PERMITTED_SHA"] = self.git("rev-parse", "HEAD").strip()

    def test_stale_pom_pin_builds_against_the_installed_version(self):
        self.stale_pom_pin()
        self.run_transport()
        import json
        receipt = json.loads((self.repo / ".jenkins-tmp/es4-tests-123/receipt.json").read_text())
        self.assertEqual("PASS", receipt["status"])

    def test_stale_pom_pin_without_the_override_resolves_the_stale_pin_and_fails(self):
        """The negative control for the test above: with the override removed, the only version mvn
        can see is the pom's stale 0.9.0, so the run must fail rather than silently test the wrong
        contracts jar. Without this, a regression to the pin would pass unnoticed."""
        self.stale_pom_pin()
        body = self.runner.read_text()
        self.assertIn('mvn -B test -Doptions-edge-contracts.version="$CI_CONTRACTS_VERSION"', body)
        self.runner.write_text(body.replace(
            'mvn -B test -Doptions-edge-contracts.version="$CI_CONTRACTS_VERSION"', 'mvn -B test'))
        self.run_transport(1)
        out = self.repo / ".jenkins-tmp/es4-tests-123"
        self.assertIn("Missing installed contracts version override", (out / "test.log").read_text())
        self.assertFalse((out / "receipt.json").exists())

    def test_the_campaigns_unqualified_maven_resolves_the_installed_version(self):
        """The campaign's maven carries no version of its own, so the run root's .mvn/maven.config is the
        only thing that can point it at the jar this build installed. The pom here pins 0.9.0 and only
        0.8.0 exists in the private repository, so a run that completes is a run whose campaign maven
        resolved 0.8.0 — the stub resolves it exactly as maven does and refuses otherwise."""
        self.stale_pom_pin()
        self.run_transport()
        import json
        receipt = json.loads((self.repo / ".jenkins-tmp/es4-tests-123/receipt.json").read_text())
        self.assertEqual("PASS", receipt["status"])
        self.assertIn("footprint-check", (self.repo / ".jenkins-tmp/es4-tests-123/test.log").read_text())

    def test_without_the_run_root_pin_the_campaigns_maven_resolves_the_stale_pin(self):
        """The negative control, and it reproduces build #825 exactly: with the write removed, the
        campaign's maven falls back to the pom's 0.9.0, which is not in this build's repository."""
        self.stale_pom_pin()
        body = self.runner.read_text()
        needle = """printf -- '-Doptions-edge-contracts.version=%s\\n' "$cv" > .mvn/maven.config"""
        self.assertIn(needle, body)
        self.runner.write_text(body.replace(needle, ":"))
        self.run_transport(1)
        out = self.repo / ".jenkins-tmp/es4-tests-123"
        log = (out / "test.log").read_text()
        self.assertIn("Could not find artifact com.optionsedge:options-edge-contracts:jar:0.9.0", log)
        self.assertFalse((out / "complete.txt").exists())

    def test_cash_window_refuses_before_any_transport(self):
        self.env["CI_FAKE_DATE"] = "1 0900 EDT"
        self.run_transport(1)
        self.assertFalse((self.root / "calls").exists())

    def test_the_run_is_bounded_to_the_ninety_minutes_the_work_measures(self):
        # 90 is from the record, not from a margin: 12.4 minutes for the suite and 37.6 for the
        # 50-case campaign in build 815 is 50.0 measured, and #800 at 40 and 812/813 at 50 are the
        # three builds that died at a cap with every test passing.
        self.run_transport()
        self.assertIn("--property=RuntimeMaxSec=5400", (self.root / "unit-argv").read_text())

    def test_the_waiver_changes_no_unit_property_at_all(self):
        # The waiver says a run may START inside the session. It says nothing about how long the work
        # takes, so it must not reach the bound, nor the memory, task or swap allowance. Only the
        # properties are compared: the unit name and the private run directory carry a fresh random
        # component per run, so the raw argv differs for reasons that are not limits.
        def properties():
            words = (self.root / "unit-argv").read_text().split()
            return [w for w in words if w.startswith("--property=")]
        self.run_transport()
        plain = properties()
        self.tearDown(); self.setUp()
        self.env["CI_FAKE_DATE"] = "1 0900 EDT"
        self.env["ALLOW_CASH_WINDOW"] = "true"
        self.run_transport()
        waived = properties()
        self.assertEqual(plain, waived)
        self.assertEqual(["--property=RuntimeMaxSec=5400"], [w for w in plain if "RuntimeMaxSec" in w])
        self.assertIn("--property=MemoryMax=4G", plain)

    def test_the_exclusion_starts_a_full_bound_plus_margin_before_the_open(self):
        # The bound and the boundary are one fact stated twice. 07:29 is the last permitted start, and
        # a run that takes the whole 90 minutes from there releases es4 at 08:59 — half an hour before
        # the 09:30 open. 07:30 is already refused, so the margin can never be less than that.
        # --check-window is the script's own entry point for this and runs no transport, so each case
        # is a verdict on the clock alone rather than a run that would claim this build's directory.
        for hhmm, refused in (("0729", False), ("0730", True), ("0829", True), ("1559", True),
                              ("1600", False), ("2359", False)):
            with self.subTest(hhmm=hhmm):
                env = dict(self.env, CI_FAKE_DATE=f"1 {hhmm} EDT")
                result = subprocess.run(["bash", str(self.runner), "--check-window"], cwd=self.repo,
                                        env=env, text=True, capture_output=True, timeout=30)
                self.assertEqual(1 if refused else 0, result.returncode, result.stderr[-500:])
                self.assertEqual(refused, "cash-window exclusion is 07:30-16:00" in result.stderr)

    def test_every_copy_of_the_exclusion_compares_against_the_same_boundary(self):
        # The boundary is written three times: once on the caller and twice inside the es4 heredoc. The
        # --check-window test above exercises only the caller, so a remote copy left at 08:30 would pass
        # it. Every weekday-window comparison in the script is extracted here instead, so a copy that
        # moves alone is a failure wherever it lives.
        hhmm = re.findall(r'"\$hhmm" -ge (\d{4})', SOURCE.read_text())
        self.assertEqual(3, len(hhmm), hhmm)
        self.assertEqual({"0730"}, set(hhmm))
        ends = re.findall(r'"\$hhmm" -lt (\d{4})', SOURCE.read_text())
        self.assertEqual(["1600"] * 3, ends)

    def test_the_es4_side_refuses_the_window_with_the_callers_check_removed(self):
        # The caller refuses first, so nothing else would ever reach the two copies on es4. With the
        # caller's call deleted the transport runs into them, and a weekday 07:30 without the waiver has
        # to die there rather than start a unit that would still be running into the pre-open margin.
        body = self.runner.read_text()
        self.assertIn("\ncheck_window\n", body)
        self.runner.write_text(body.replace("\ncheck_window\n", "\n", 1))
        self.env["CI_FAKE_DATE"] = "1 0730 EDT"
        self.run_transport(1)
        # No receipt and no completion marker: the refusal happened before anything ran, not after.
        out = self.repo / ".jenkins-tmp/es4-tests-123"
        self.assertFalse((out / "complete.txt").exists())
        self.assertFalse((out / "receipt.json").exists())

    def test_a_weekend_start_is_never_in_the_exclusion_however_the_clock_reads(self):
        # The exclusion is a weekday fact. A Saturday 09:00 looks exactly like the refused weekday
        # case on the clock alone, so a boundary check that dropped the weekday test would still pass
        # every case above and refuse every weekend build.
        for day in ("6", "7"):
            with self.subTest(day=day):
                env = dict(self.env, CI_FAKE_DATE=f"{day} 0900 EDT")
                result = subprocess.run(["bash", str(self.runner), "--check-window"], cwd=self.repo,
                                        env=env, text=True, capture_output=True, timeout=30)
                self.assertEqual(0, result.returncode, result.stderr[-500:])

    def test_an_explicit_waiver_runs_inside_the_cash_window_on_both_sides_of_the_transport(self):
        # The es4 side checks the window twice more, with the waiver carried as a positional, so a run
        # that reaches its receipt proves the waiver crossed: a missing positional is exit 64 there.
        self.env["CI_FAKE_DATE"] = "1 0900 EDT"
        self.env["ALLOW_CASH_WINDOW"] = "true"
        self.run_transport()
        import json
        receipt = json.loads((self.repo / ".jenkins-tmp/es4-tests-123/receipt.json").read_text())
        self.assertEqual("PASS", receipt["status"])

    def test_an_unrecognised_waiver_is_refused_rather_than_read_as_either_answer(self):
        for value in ("TRUE", "True", "1", "yes", "no", "0"):
            with self.subTest(value=value):
                self.env["CI_FAKE_DATE"] = "1 0900 EDT"
                self.env["ALLOW_CASH_WINDOW"] = value
                result = self.run_transport(64)
                self.assertIn("ALLOW_CASH_WINDOW must be true or false", result.stderr)
                self.assertFalse((self.root / "calls").exists())

    def test_an_empty_waiver_is_absence_so_the_window_still_refuses(self):
        # Empty is not supplied, which is what a blank Jenkins field gives: it takes the safe default
        # and refuses, rather than being a third state the operator has to reason about.
        self.env["CI_FAKE_DATE"] = "1 0900 EDT"
        self.env["ALLOW_CASH_WINDOW"] = ""
        self.run_transport(1)
        self.assertFalse((self.root / "calls").exists())

    def test_the_waiver_does_not_relax_the_window_outside_the_cash_session(self):
        # Nothing about the waiver depends on the clock: it is only ever consulted inside the window,
        # so the ordinary out-of-hours run must be byte-identical with it set and with it absent.
        self.env["CI_FAKE_DATE"] = "6 0700 EDT"
        self.env["ALLOW_CASH_WINDOW"] = "true"
        self.run_transport()
        self.assertEqual("PASS", __import__("json").loads(
            (self.repo / ".jenkins-tmp/es4-tests-123/receipt.json").read_text())["status"])

    def cancellation(self, command):
        run = self.remote / "run.123.fixture"
        run.mkdir(parents=True)
        if command is not None:
            self.write(self.root / "unit-exec", command.replace("RUN", str(run)))
        body = SOURCE.read_text().split("<<'REMOTE_FINISH'", 1)[1].split("\n", 1)[1].split("\nREMOTE_FINISH", 1)[0]
        body = body.replace("/home/abhinav/ci/gateway-tests", str(self.remote))
        result = subprocess.run(["bash", "-s", "--", str(run)], input=body, text=True,
                                capture_output=True, env=self.env, timeout=10)
        self.assertEqual(0, result.returncode, result.stderr)
        stopped = self.root / "unit-stops"
        return stopped.read_text() if stopped.exists() else ""

    def test_cancellation_stops_only_its_proven_unit(self):
        self.assertIn("stop oe-gateway-run-123-fixture", self.cancellation("bash RUN/run-tests.sh"))

    def test_cancellation_does_not_stop_an_unrelated_command(self):
        self.assertEqual("", self.cancellation("unrelated-worker"))

    def test_cancellation_does_not_stop_an_absent_unit(self):
        self.assertEqual("", self.cancellation(None))

    def test_existing_local_attempt_is_not_reused(self):
        (self.repo / ".jenkins-tmp/es4-tests-123").mkdir(parents=True)
        self.run_transport(1)
        self.assertFalse((self.root / "calls").exists())


class StageBackstopTest(unittest.TestCase):
    """The Jenkins stage timeout and the worker's own bound, read from the files that carry them."""

    JENKINSFILE = Path(__file__).resolve().parents[2] / "Jenkinsfile"

    def worker_bounds(self):
        # Every literal the script assigns, so a bound added later is still what the stage must outlast.
        return [int(m) for m in re.findall(r"run_bound_sec=(\d+)", SOURCE.read_text())]

    def stage_backstop(self):
        text = self.JENKINSFILE.read_text()
        after = text[text.index("stage('Test') {"):]
        return int(re.search(r"options \{ timeout\(time: (\d+), unit: 'MINUTES'\) \}", after).group(1)) * 60

    def test_the_stage_backstop_outlasts_the_longest_worker_bound(self):
        # Build 814 is why this is a test and not a comment: the waived bound went to 90 minutes while the
        # stage kept the 60 it had been derived from, so Jenkins cancelled the step first. The two bounds
        # are not interchangeable — the worker's restores the tree, names the signal and scps the evidence
        # back, and the stage's leaves the evidence on es4 — so the one that fires must be the worker's.
        self.assertGreater(self.stage_backstop(), max(self.worker_bounds()))

    def test_the_backstop_leaves_room_for_the_setup_and_the_evidence_it_has_to_survive(self):
        # Strictly greater is not enough: the stage also pays for its own work either side of the driver
        # — the fixtures, the two tree verifies and the scp-back — so a backstop one minute past the bound
        # would still abort a run the worker completed. (The contracts install is not in that budget; it is
        # a stage of its own.) 20 minutes is the MINIMUM slack asserted, not the slack there is: 115 over
        # 90 leaves 25, so the number may be raised to what is measured without the test moving.
        self.assertGreaterEqual(self.stage_backstop() - max(self.worker_bounds()), 20 * 60)

    def test_the_bound_is_still_readable_where_the_backstop_reads_it(self):
        # A negative control for the two assertions above: if the literal stops being found, the max()
        # raises or narrows, and a vacuous pass would be worse than a failure. It is one bound, so a
        # second one appearing is a finding too — that is the shape build 814 came from.
        self.assertEqual([5400], self.worker_bounds())


if __name__ == "__main__":
    unittest.main()
