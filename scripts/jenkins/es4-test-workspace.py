#!/usr/bin/env python3
"""Preserve bounded, inactive CI scratch directories before the existing count gate."""
import os
import re
import stat
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path("/home/abhinav/ci/gateway-tests")
MIN_AGE = 24 * 60 * 60
MAX_QUARANTINE = 3
RUN = re.compile(r"run\.[0-9]+\.[A-Za-z0-9]{8}")
QUARANTINE = "preserved-orphans"


def private_directory(path):
    info = path.lstat()
    if not stat.S_ISDIR(info.st_mode) or info.st_uid != os.getuid() or info.st_mode & 0o022:
        raise RuntimeError("CI workspace is not an owned, non-writable directory: " + path.name)
    return info


def process_cwd(proc):
    def metadata():
        try:
            return proc.stat()
        except FileNotFoundError:
            raise  # only this error proves the PID is gone
        except OSError as error:
            raise RuntimeError("Cannot confirm process identity") from error
    before = metadata()
    if before.st_uid != os.getuid():
        raise RuntimeError("Process owner changed before inspection")
    try:
        value = os.readlink(proc / "cwd")
    except PermissionError:
        # Container processes may have our uid but disallow unprivileged ptrace.
        # Read only this already-filtered numeric process link; never ignore it.
        try:
            result = subprocess.run(
                ["/usr/bin/sudo", "-n", "/usr/bin/readlink", "-z", "--", str(proc / "cwd")],
                capture_output=True, timeout=5)
        except (OSError, subprocess.TimeoutExpired) as error:
            raise RuntimeError("Restricted process inspection could not run") from error
        after = metadata()
        if (after.st_uid, after.st_ino, after.st_dev) != (before.st_uid, before.st_ino, before.st_dev):
            raise RuntimeError("Process identity changed during inspection")
        if result.returncode != 0:
            raise RuntimeError("Cannot inspect restricted user process")
        raw = result.stdout
        if not raw.startswith(b"/") or not raw.endswith(b"\0") or raw.count(b"\0") != 1:
            raise RuntimeError("Invalid restricted process directory")
        return os.fsdecode(raw[:-1])
    after = metadata()
    if (after.st_uid, after.st_ino, after.st_dev) != (before.st_uid, before.st_ino, before.st_dev):
        raise RuntimeError("Process identity changed during inspection")
    return value


def process_directories(proc_root=Path("/proc")):
    result = []
    for proc in proc_root.iterdir():
        if not re.fullmatch(r"[0-9]+", proc.name):
            continue
        try:
            if proc.stat().st_uid != os.getuid():
                continue
            result.append(process_cwd(proc))
        except FileNotFoundError:
            try:
                proc.stat()
            except FileNotFoundError:
                continue  # confirmed process exit, not an inspection failure
            except OSError as error:
                raise RuntimeError("Cannot confirm exited user process") from error
            raise RuntimeError("User process directory is unreadable while PID remains")
        except PermissionError as error:
            raise RuntimeError("Cannot exclude an active user process") from error
    return result


def running_ci_units():
    result = subprocess.run(
        ["systemctl", "--user", "list-units", "--all", "--plain", "--no-legend", "oe-gateway-*"],
        check=True, text=True, capture_output=True, timeout=10)
    # Unit output columns are UNIT LOAD ACTIVE SUB DESCRIPTION. Fail closed for live units.
    for line in result.stdout.splitlines():
        words = line.lstrip("● ").split()
        if len(words) >= 4 and words[2] in ("active", "activating", "deactivating", "reloading"):
            return True
    return False


def recover(root, now, active_paths, units_active=False):
    """Tests supply an isolated root and process observations; CLI fixes the real root."""
    info = private_directory(root)
    directories = [p for p in root.iterdir() if stat.S_ISDIR(p.lstat().st_mode)]
    if len(directories) < 4:
        return []
    if units_active:
        raise RuntimeError("An ES4 gateway CI unit is still active; no directories moved")
    saved = root / QUARANTINE
    existing = []
    if saved.exists() or saved.is_symlink():
        saved_info = private_directory(saved)
        if saved_info.st_dev != info.st_dev:
            raise RuntimeError("CI quarantine crosses filesystem")
        existing = list(saved.iterdir())
        for item in existing:
            if not RUN.fullmatch(item.name):
                raise RuntimeError("Unexpected retained CI entry")
            private_directory(item)
    if len(existing) > MAX_QUARANTINE:
        raise RuntimeError("CI quarantine capacity exceeded")
    candidates = []
    for path in directories:
        if not RUN.fullmatch(path.name):
            continue
        metadata = private_directory(path)
        if metadata.st_dev != info.st_dev:
            raise RuntimeError("CI workspace crosses filesystem")
        if now - metadata.st_mtime < MIN_AGE:
            continue
        prefix = str(path) + os.sep
        if any(cwd == str(path) or cwd.startswith(prefix) for cwd in active_paths):
            raise RuntimeError("A retained CI workspace is still in use: " + path.name)
        candidates.append((metadata.st_mtime, path, metadata.st_ino, metadata.st_dev))
    candidates.sort(key=lambda item: (item[0], item[1].name))
    chosen = candidates[:MAX_QUARANTINE - len(existing)]
    # Creating quarantine itself adds a top-level directory. Never move anything unless
    # the bounded plan actually clears the unchanged four-directory gate.
    remaining = len(directories) - len(chosen) + (0 if saved in directories else 1)
    if not chosen or remaining >= 4:
        raise RuntimeError("No bounded stale-workspace recovery is available")
    for _, path, _, _ in chosen:
        target = saved / path.name
        if target.exists() or target.is_symlink():
            raise RuntimeError("Refusing to replace retained CI evidence")
    if not saved.exists():
        saved.mkdir(mode=0o700)
    moved = []
    for _, path, inode, device in chosen:
        # Only the serial Jenkins gateway job calls this helper. Revalidate ownership,
        # age and inode before the atomic same-filesystem rename.
        before = private_directory(path)
        if before.st_ino != inode or before.st_dev != device or now - before.st_mtime < MIN_AGE:
            raise RuntimeError("CI workspace changed during recovery")
        path.rename(saved / path.name)
        moved.append(path.name)
        print("Preserved CI evidence: " + path.name, flush=True)
    return moved


def main():
    if len(sys.argv) != 2 or Path(sys.argv[1]) != ROOT:
        raise SystemExit("Only the fixed ES4 gateway CI root is allowed")
    if not ROOT.exists():
        return
    private_directory(ROOT)
    # Inspect processes/units before any filesystem mutation. The Jenkins job is serial.
    moved = recover(ROOT, time.time(), process_directories(), running_ci_units())
    print("Preserved inactive CI workspaces: " + (", ".join(moved) if moved else "none needed"))


if __name__ == "__main__":
    main()
