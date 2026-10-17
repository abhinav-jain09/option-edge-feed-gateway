"""Finite transport faults, with SSH/SCP/Maven stubbed; no host, broker or large suite is contacted."""
import hashlib
import os
from pathlib import Path
import shutil
import signal
import sys
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
        for name in ("footprint-reverify.sh", "footprint-reqstate.sh"):
            self.script(self.repo / "scripts" / name, 'echo footprint-check')
        self.script(self.jdk / "bin/java", 'echo \'openjdk version "21"\' >&2')
        self.maven(0)
        self.script(self.bin / "date", 'printf "%s\\n" "${CI_FAKE_DATE:-6 0700}"')
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
for source in args[:-1]:
 p=pathlib.Path(local(source));target=dest/p.name if dest.is_dir() else dest
 if p.is_dir():shutil.copytree(p,target,dirs_exist_ok=True)
 else:shutil.copy2(p,target)
''', True)
        # Only deployment-specific filesystem/JDK literals change in the test copy. Guard bodies are unchanged.
        self.runner = self.root / "transport.sh"
        self.write(self.runner, SOURCE.read_text().replace("/home/abhinav/ci/gateway-tests", str(self.remote))
                   .replace("/usr/lib/jvm/java-21-openjdk", str(self.jdk)), True)
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
                    "CI_CALLS": str(self.root / "calls"), "CI_FAKE_DATE": "6 0700"}

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
        receipt = (out / "receipt.txt").read_text()
        self.assertIn("source=" + self.sha + "\n", receipt)
        self.assertIn("contracts_source=" + "c" * 40 + "\n", receipt)
        self.assertIn("build_number=123\nstatus=PASS\n", receipt)
        self.assertTrue((out / "surefire-reports/TEST-fixture.xml").is_file())
        self.assertEqual([], list(self.remote.iterdir()))

    def test_maven_failure_cannot_publish_a_pass_receipt(self):
        self.maven(2)
        self.run_transport(2)
        self.assertFalse((self.repo / ".jenkins-tmp/es4-tests-123/receipt.txt").exists())
        self.assertEqual([], list(self.remote.iterdir()))

    def test_wrong_artifact_or_source_fails_before_any_transport(self):
        self.jar.write_text("altered artifact")
        self.run_transport(1)
        self.assertFalse((self.root / "calls").exists())

    def test_wrong_source_refuses_before_any_transport(self):
        self.env["PERMITTED_SHA"] = "f" * 40
        self.run_transport(1)
        self.assertFalse((self.root / "calls").exists())

    def test_wrong_dependency_pin_refuses_before_any_transport(self):
        p = self.repo / "pom.xml"
        p.write_text(p.read_text().replace("0.8.0", "0.9.0"))
        self.git("add", "pom.xml")
        self.git("commit", "-qm", "pin mismatch fixture")
        self.env["PERMITTED_SHA"] = self.git("rev-parse", "HEAD").strip()
        self.run_transport(1)
        self.assertFalse((self.root / "calls").exists())

    def test_cash_window_refuses_before_any_transport(self):
        self.env["CI_FAKE_DATE"] = "1 0900"
        self.run_transport(1)
        self.assertFalse((self.root / "calls").exists())

    def cancellation(self, matched, own_group):
        run = self.remote / "run.123.fixture"
        run.mkdir(parents=True)
        child = subprocess.Popen([sys.executable, "-c", "import time;time.sleep(60)", str(run)],
                                 start_new_session=own_group)
        try:
            self.write(run / "test.pid", str(child.pid))
            proc_root = self.root / "proc"
            self.write(proc_root / str(child.pid) / "cmdline", "fixture " + (str(run) if matched else "unrelated-worker"))
            body = SOURCE.read_text().split("<<'REMOTE_FINISH'", 1)[1].split("\n", 1)[1].split("\nREMOTE_FINISH", 1)[0]
            body = body.replace("/home/abhinav/ci/gateway-tests", str(self.remote)).replace("/proc/", str(proc_root) + "/")
            result = subprocess.run(["bash", "-s", "--", str(run)], input=body, text=True,
                                    capture_output=True, timeout=15)
            self.assertEqual(0, result.returncode, result.stderr)
            if matched and own_group:
                self.assertEqual(-signal.SIGTERM, child.wait(timeout=2))
            else:
                self.assertIsNone(child.poll(), "a different worker/group must survive")
        finally:
            if child.poll() is None:
                child.terminate()
            child.wait(timeout=3)

    def test_cancellation_stops_only_its_proven_process_group(self):
        self.cancellation(True, True)

    def test_cancellation_does_not_stop_a_reused_unrelated_pid(self):
        self.cancellation(False, True)

    def test_cancellation_does_not_signal_a_shared_process_group(self):
        self.cancellation(True, False)

    def test_existing_local_attempt_is_not_reused(self):
        (self.repo / ".jenkins-tmp/es4-tests-123").mkdir(parents=True)
        self.run_transport(1)
        self.assertFalse((self.root / "calls").exists())


if __name__ == "__main__":
    unittest.main()
