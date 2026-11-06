#!/usr/bin/env python3
import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

SOURCE = Path(__file__).with_name("es4-test-workspace.py")
spec = importlib.util.spec_from_file_location("workspace_recovery", SOURCE)
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
NOW = 2_000_000_000


class RecoveryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.root.chmod(0o700)

    def tearDown(self):
        self.temp.cleanup()

    def run_dir(self, number, age=2 * m.MIN_AGE, parent=None):
        path = (parent or self.root) / ("run." + str(number) + ".ABCDEFGH")
        path.mkdir(mode=0o700)
        (path / "evidence.bin").write_bytes(bytes(range(256)))
        os.utime(path, (NOW - age, NOW - age))
        return path

    def four(self):
        return [self.run_dir(i) for i in range(1, 5)]

    def test_preserves_three_old_orphans_and_leaves_recent_run_untouched(self):
        old = [self.run_dir(i) for i in range(1, 4)]
        recent = self.run_dir(846, 60)
        moved = m.recover(self.root, NOW, [])
        self.assertEqual([p.name for p in old], moved)
        self.assertTrue(recent.is_dir())
        for path in old:
            self.assertEqual(bytes(range(256)), (self.root / m.QUARANTINE / path.name / "evidence.bin").read_bytes())
        self.assertEqual(2, sum(p.is_dir() for p in self.root.iterdir()))
        self.assertEqual([], m.recover(self.root, NOW, [str(recent)]))

    def test_active_process_or_ci_unit_prevents_all_moves(self):
        old = self.four()
        for active, unit in [([str(old[0] / "gateway")], False), ([], True)]:
            with self.assertRaises(RuntimeError):
                m.recover(self.root, NOW, active, unit)
            self.assertTrue(all(p.is_dir() for p in old))
            self.assertFalse((self.root / m.QUARANTINE).exists())

    def test_recent_workspaces_do_not_bypass_existing_count_gate(self):
        old = [self.run_dir(i, 60) for i in range(4)]
        with self.assertRaises(RuntimeError):
            m.recover(self.root, NOW, [])
        self.assertTrue(all(p.is_dir() for p in old))

    def test_symlinked_quarantine_is_never_followed(self):
        old = self.four()
        with tempfile.TemporaryDirectory() as outside:
            (self.root / m.QUARANTINE).symlink_to(outside, target_is_directory=True)
            with self.assertRaises(RuntimeError):
                m.recover(self.root, NOW, [])
            self.assertTrue(all(p.is_dir() for p in old))
            self.assertEqual([], list(Path(outside).iterdir()))

    def test_existing_evidence_is_never_overwritten(self):
        q = self.root / m.QUARANTINE
        q.mkdir(mode=0o700)
        duplicate = self.run_dir(1, parent=q)
        old = [self.run_dir(i) for i in range(1, 4)]
        with self.assertRaises(RuntimeError):
            m.recover(self.root, NOW, [])
        self.assertTrue(all(p.is_dir() for p in old))
        self.assertEqual(bytes(range(256)), (duplicate / "evidence.bin").read_bytes())

    def test_quarantine_capacity_is_not_silently_expanded(self):
        q = self.root / m.QUARANTINE
        q.mkdir(mode=0o700)
        for i in range(10, 14):
            self.run_dir(i, parent=q)
        old = [self.run_dir(i) for i in range(1, 4)]
        with self.assertRaises(RuntimeError):
            m.recover(self.root, NOW, [])
        self.assertTrue(all(p.is_dir() for p in old))

    def test_full_quarantine_and_insufficient_plan_preserve_all_evidence(self):
        q = self.root / m.QUARANTINE
        q.mkdir(mode=0o700)
        for i in range(10, 13):
            self.run_dir(i, parent=q)
        old = [self.run_dir(i) for i in range(1, 4)]
        with self.assertRaises(RuntimeError):
            m.recover(self.root, NOW, [])
        self.assertTrue(all(p.is_dir() for p in old))
        self.assertEqual(3, len(list(q.iterdir())))

    def test_plan_that_cannot_clear_count_moves_nothing(self):
        old = [self.run_dir(i) for i in range(7)]
        with self.assertRaises(RuntimeError):
            m.recover(self.root, NOW, [])
        self.assertTrue(all(p.is_dir() for p in old))
        self.assertFalse((self.root / m.QUARANTINE).exists())

    def test_writable_root_is_rejected_without_any_move(self):
        old = self.four()
        self.root.chmod(0o777)
        with self.assertRaises(RuntimeError):
            m.recover(self.root, NOW, [])
        self.root.chmod(0o700)
        self.assertTrue(all(p.is_dir() for p in old))

    def test_restricted_same_user_cwd_uses_bounded_readonly_link_inspection(self):
        proc = self.root / "13176"
        proc.mkdir()
        with patch.object(m.os, "readlink", side_effect=PermissionError), patch.object(m.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, b"/\0", b"")) as call:
            self.assertEqual(["/"], m.process_directories(self.root))
            call.assert_called_once_with(["/usr/bin/sudo", "-n", "/usr/bin/readlink", "-z", "--", str(proc / "cwd")], capture_output=True, timeout=5)

    def test_restricted_inspection_refusal_and_malformed_result_fail_closed(self):
        proc = self.root / "13176"
        proc.mkdir()
        for code, value in [(1, b""), (0, b"relative\0"), (0, b"/\0extra\0"), (0, b"/")]:
            with patch.object(m.os, "readlink", side_effect=PermissionError), patch.object(m.subprocess, "run", return_value=subprocess.CompletedProcess([], code, value, b"")):
                with self.assertRaises(RuntimeError):
                    m.process_directories(self.root)

    def test_missing_inspection_binary_and_timeout_are_not_exited_processes(self):
        proc = self.root / "13176"
        proc.mkdir()
        for failure in [FileNotFoundError("sudo missing"), PermissionError("sudo not executable"), subprocess.TimeoutExpired("readlink", 5)]:
            with patch.object(m.os, "readlink", side_effect=PermissionError), patch.object(m.subprocess, "run", side_effect=failure):
                with self.assertRaises(RuntimeError):
                    m.process_directories(self.root)

    def test_exited_restricted_pid_is_skipped_only_after_failed_inspection(self):
        proc = self.root / "13176"
        proc.mkdir()
        def exited(*args, **kwargs):
            proc.rmdir()
            return subprocess.CompletedProcess([], 1, b"", b"")
        with patch.object(m.os, "readlink", side_effect=PermissionError), patch.object(m.subprocess, "run", side_effect=exited):
            self.assertEqual([], m.process_directories(self.root))

    def test_restricted_active_workspace_still_blocks_recovery(self):
        old = self.four()
        proc = self.root / "13176"
        proc.mkdir()
        with patch.object(m.os, "readlink", side_effect=PermissionError), patch.object(m.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, os.fsencode(str(old[0])) + b"\0", b"")):
            active = m.process_directories(self.root)
            with self.assertRaises(RuntimeError):
                m.recover(self.root, NOW, active)
        self.assertTrue(all(p.is_dir() for p in old))

    def test_cli_refuses_every_root_other_than_fixed_es4_root(self):
        old = self.four()
        result = subprocess.run([sys.executable, str(SOURCE), str(self.root)], capture_output=True)
        self.assertNotEqual(0, result.returncode)
        self.assertTrue(all(p.is_dir() for p in old))


if __name__ == "__main__":
    unittest.main()
