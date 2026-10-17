"""Finite capture faults; the real comparison runs with a stub mutation runner."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SOURCE = Path(__file__).with_name("footprint-reverify.sh").read_text()
KEY = "fixture obligation"
ROW = {"requirement": "G-R1", "clause": "fixture", "documentText": "obligation",
       "file": "fixture.java", "occurrence": 1, "occurrencesInFile": 1,
       "command": ["fixture"], "patch": {"old": "a", "new": "b"},
       "status": "KILLED", "assertionFailures": ["Fixture.required"], "killedByThrow": {}}


def run(actual, source=SOURCE, args=(), malformed=False, symlink_parent=False):
    with tempfile.TemporaryDirectory(prefix="footprint-capture-") as directory:
        root = Path(directory)
        scripts = root / "scripts"
        scripts.mkdir()
        (scripts / "footprint-reverify.sh").write_text(source)
        (scripts / "footprint-gated-specs").write_text("scripts/footprint-campaign.spec.json\n")
        (scripts / "footprint-campaign.spec.json").write_text(json.dumps({"mutations": [{"key": KEY}]}))
        (root / "ES-FOOTPRINT-CAMPAIGN.json").write_text(json.dumps({KEY: ROW}))
        (root / "actual.json").write_text("{" if malformed else json.dumps(actual))
        (scripts / "footprint-mutate.py").write_text(
            "import pathlib,sys\npathlib.Path(sys.argv[2]).write_bytes(pathlib.Path('actual.json').read_bytes())\n")
        target = root / "target"
        outside = root / "outside"
        outside.mkdir()
        (outside / "footprint-reverify.json").write_bytes(b"sentinel")
        if symlink_parent:
            target.symlink_to(outside, target_is_directory=True)
        else:
            target.mkdir()
            (target / "footprint-reverify.json").write_bytes(b"stale prior run")
        env = {k: v for k, v in os.environ.items() if k not in ("JENKINS_URL", "GITHUB_ACTIONS", "CI")}
        result = subprocess.run(["bash", str(scripts / "footprint-reverify.sh"), *args],
                                env=env, capture_output=True, text=True, timeout=10)
        path = target / "footprint-reverify.json"
        captured = path.read_bytes() if not target.is_symlink() and path.is_file() else None
        return result, captured, (outside / "footprint-reverify.json").read_bytes()


class CaptureTest(unittest.TestCase):
    def require_capture_on_comparison_failure(self, source=SOURCE):
        actual = {KEY: {**ROW, "assertionFailures": ["Fixture.required", "Fixture.additional"]}}
        result, captured, _ = run(actual, source=source)
        self.assertNotEqual(0, result.returncode)  # Existing exact witness-set check is preserved.
        self.assertIsNotNone(captured, "failed comparison must retain its actual record")
        self.assertEqual(actual, json.loads(captured))

    def test_comparison_failure_retains_the_real_rerun(self):
        self.require_capture_on_comparison_failure()

    def test_success_retains_the_real_rerun(self):
        result, captured, _ = run({KEY: ROW})
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual({KEY: ROW}, json.loads(captured))

    def test_malformed_run_cannot_archive_stale_prior_evidence(self):
        result, captured, _ = run({}, malformed=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIsNone(captured)

    def test_invalid_gate_arguments_cannot_archive_stale_prior_evidence(self):
        result, captured, _ = run({KEY: ROW}, args=("unexpected",))
        self.assertNotEqual(0, result.returncode)
        self.assertIsNone(captured)

    def test_manual_partial_run_does_not_publish_gate_evidence(self):
        result, captured, _ = run({KEY: ROW}, args=("--not-the-gate", "scripts/footprint-campaign.spec.json"))
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIsNone(captured)

    def test_symlinked_parent_is_refused_before_deleting_anything(self):
        result, captured, sentinel = run({KEY: ROW}, symlink_parent=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("refusing symlinked target directory", result.stderr)
        self.assertIsNone(captured)
        self.assertEqual(b"sentinel", sentinel)

    def require_oversize_refused(self, source=SOURCE):
        result, captured, _ = run({KEY: {**ROW, "padding": "a" * (8 << 20)}}, source=source)
        self.assertNotEqual(0, result.returncode, "oversized evidence must be refused")
        self.assertIn("8 MiB evidence budget", result.stdout + result.stderr)
        self.assertIsNone(captured)

    def test_oversized_record_is_refused(self):
        self.require_oversize_refused()

    def test_negative_control_removing_capture_loses_failure_evidence(self):
        start = SOURCE.index("# Preserve the actual full-gate rerun")
        end = SOURCE.index('python3 - "$RECORD" "$OUT"', start)
        with self.assertRaisesRegex(AssertionError, "retain its actual record"):
            self.require_capture_on_comparison_failure(SOURCE[:start] + SOURCE[end:])

    def test_negative_control_raising_budget_accepts_oversized_record(self):
        with self.assertRaisesRegex(AssertionError, "oversized evidence must be refused"):
            self.require_oversize_refused(SOURCE.replace("limit = 8 << 20", "limit = 9 << 20"))


if __name__ == "__main__":
    unittest.main()
