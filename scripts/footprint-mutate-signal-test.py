"""The campaign's restore path under a signal; no Maven, broker or real mutation target is used.

A 50-case campaign on es4 runs under a systemd RuntimeMaxSec bound, so SIGTERM arriving in the
middle of a mutation is an ordinary outcome, not an exotic one. What the run must then leave behind
is the original tree and one line naming the signal.
"""
import json
import os
from pathlib import Path
import signal
import subprocess
import tempfile
import time
import unittest

MUTATE = Path(__file__).with_name("footprint-mutate.py")
ORIGINAL = "class Fixture { int keep() { return 1; } }\n"
MUTANT = "class Fixture { int keep() { return 2; } }\n"


class SignalRestoreTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="footprint-signal-")
        # The script under test is copied OUTSIDE the campaign root: the campaign refuses a dirty
        # tree, and its own copy sitting in the root is exactly that.
        self.root = Path(self.tmp.name) / "repo"
        self.root.mkdir()
        self.target = self.root / "Fixture.java"
        self.target.write_text(ORIGINAL)
        # The baseline must pass and the mutation run must then block, so the signal is guaranteed to
        # arrive while the tree is mutated and a .bak exists — the only window the defect lived in.
        self.runner = self.root / "runner.sh"
        self.runner.write_text(
            "#!/usr/bin/env bash\n"
            'if [ -f "$PWD/started" ]; then echo blocking; sleep 60; fi\n'
            'touch "$PWD/started"\n'
            "echo 'Tests run: 1, Failures: 0, Errors: 0'\necho 'BUILD SUCCESS'\n")
        self.runner.chmod(0o755)
        self.spec = self.root / "spec.json"
        self.spec.write_text(json.dumps({
            "root": str(self.root), "kind": "maven", "command": [str(self.runner)],
            "mutations": [{"key": "G-R1 fixture", "file": "Fixture.java", "clause": "fixture",
                           "requirement": "G-R1", "documentText": "fixture",
                           "old": "return 1;", "new": "return 2;"}]}))
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.name", "CI unit fixture")
        self.git("config", "user.email", "fixture@example.invalid")
        self.git("add", "-A")
        self.git("commit", "-qm", "fixture")

    def tearDown(self):
        self.tmp.cleanup()

    def git(self, *args):
        subprocess.run(["git", "-C", str(self.root), *args], check=True, capture_output=True)

    def terminate_mid_mutation(self, source=None):
        script = Path(self.tmp.name) / "mutate.py"
        script.write_text(source if source is not None else MUTATE.read_text())
        child = subprocess.Popen(["python3", str(script), str(self.spec), str(self.root / "out.json")],
                                 cwd=self.root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            if self.target.read_text() == MUTANT:
                break
            if child.poll() is not None:
                self.fail("the run ended before it mutated anything: " + child.communicate()[0][-2000:])
            time.sleep(0.05)
        else:
            child.kill()
            self.fail("the fixture never reached the mutated state")
        child.send_signal(signal.SIGTERM)
        return child.wait(timeout=30), child.communicate()[0]

    def test_a_signal_mid_mutation_restores_the_tree_and_names_the_signal(self):
        code, out = self.terminate_mid_mutation()
        self.assertIn("interrupted by signal 15", out)
        self.assertEqual(ORIGINAL, self.target.read_text())
        self.assertFalse((self.root / "Fixture.java.bak").exists())
        # 130 is what the handler chose. An unhandled error on the way out would report 1 instead,
        # and a reader would be looking for a broken campaign rather than a cap that was too short.
        self.assertEqual(130, code)
        self.assertNotIn("Traceback", out)

    def test_negative_control_an_unconditional_second_restore_buries_the_reason(self):
        # With the guard removed the mutation loop's finally moves a backup the handler has already
        # consumed; this is build 813's failure exactly, and it must be what this test sees.
        source = MUTATE.read_text().replace(
            """                if path in _ACTIVE['restored']:
                    continue
""", "")
        self.assertNotIn("_ACTIVE['restored']:\n                    continue", source)
        code, out = self.terminate_mid_mutation(source)
        self.assertIn("interrupted by signal 15", out)
        self.assertIn("FileNotFoundError", out)
        self.assertNotEqual(130, code)


if __name__ == "__main__":
    unittest.main(verbosity=2)
