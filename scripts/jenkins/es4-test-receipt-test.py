"""Bounded receipt faults; no host or application is contacted."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("receipt", Path(__file__).with_name("es4-test-receipt.py"))
receipt = importlib.util.module_from_spec(spec)
spec.loader.exec_module(receipt)


class ReceiptTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.reports = self.root / "surefire-reports"
        self.reports.mkdir()
        self.xml = self.reports / "TEST-fixture.xml"
        self.xml.write_text('<testsuite tests="2" failures="0" errors="0" skipped="1"/>')
        (self.root / "test.log").write_text("finite successful test\n")
        (self.root / "complete.txt").write_text("a" * 40 + "\n")
        self.args = [str(self.root), "a" * 40, "b" * 40, "c" * 64, "123"]

    def tearDown(self):
        self.tmp.cleanup()

    def test_valid_receipt_binds_all_evidence_and_sources(self):
        receipt.main(["write", *self.args])
        receipt.main(["verify", *self.args])
        model = json.loads((self.root / "receipt.json").read_text())
        self.assertEqual((2, 1), (model["tests"], model["skipped"]))
        self.assertEqual([self.xml.name], list(model["reports"]))

    def test_changed_report_or_log_cannot_reuse_receipt(self):
        receipt.main(["write", *self.args])
        self.xml.write_text('<testsuite tests="3"/>')
        with self.assertRaises(ValueError):
            receipt.main(["verify", *self.args])

    def test_other_source_cannot_reuse_receipt(self):
        receipt.main(["write", *self.args])
        self.args[1] = "d" * 40
        with self.assertRaises(ValueError):
            receipt.main(["verify", *self.args])

    def test_missing_and_empty_reports_refuse_a_receipt(self):
        self.xml.unlink()
        with self.assertRaises(ValueError):
            receipt.main(["write", *self.args])
        self.xml.write_text('<testsuite tests="0"/>')
        with self.assertRaises(ValueError):
            receipt.main(["write", *self.args])

    def test_failed_or_all_skipped_reports_refuse_a_receipt(self):
        for xml in ('<testsuite tests="2" failures="1"/>', '<testsuite tests="2" errors="1"/>',
                    '<testsuite tests="2" skipped="2"/>'):
            self.xml.write_text(xml)
            with self.assertRaises(ValueError):
                receipt.main(["write", *self.args])

    def test_missing_completion_marker_refuses_successful_test_reports(self):
        (self.root / "complete.txt").unlink()
        with self.assertRaises(ValueError):
            receipt.main(["write", *self.args])

    def test_collector_preserves_failed_report_without_allowing_a_pass(self):
        self.xml.write_text('<testsuite tests="2" failures="1"/>')
        output = self.root / "collected"
        output.mkdir()
        receipt.main(["collect", str(self.reports), str(output)])
        self.assertEqual(self.xml.read_bytes(), (output / "surefire-reports" / self.xml.name).read_bytes())
        with self.assertRaises(ValueError):
            receipt.evidence(output)

    def test_collector_bounds_bytes_and_refuses_symbolic_reports(self):
        output = self.root / "collected"
        output.mkdir()
        with patch.object(receipt, "MAX_REPORT_BYTES", 8), self.assertRaises(ValueError):
            receipt.collect_reports(self.reports, output)
        self.assertEqual(0, (output / "surefire-reports" / self.xml.name).stat().st_size)
        other = self.root / "second"
        other.mkdir()
        target = self.root / "outside.xml"
        target.write_bytes(self.xml.read_bytes())
        self.xml.unlink()
        self.xml.symlink_to(target)
        with self.assertRaises(ValueError):
            receipt.collect_reports(self.reports, other)

    def test_symlinked_evidence_is_not_accepted(self):
        other = self.root / "outside.xml"
        other.write_bytes(self.xml.read_bytes())
        self.xml.unlink()
        self.xml.symlink_to(other)
        with self.assertRaises(ValueError):
            receipt.main(["write", *self.args])


if __name__ == "__main__":
    unittest.main()
