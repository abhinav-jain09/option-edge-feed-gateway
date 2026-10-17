#!/usr/bin/env python3
"""Bind passing finite JUnit reports and the log to this exact Jenkins test run."""
import hashlib
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def digest(path, limit):
    if path.is_symlink() or not path.is_file() or path.stat().st_size > limit:
        raise ValueError(f"missing, symbolic or oversized test evidence: {path}")
    sha = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(65536), b""):
            sha.update(block)
    return sha.hexdigest()


def evidence(root):
    directory = root / "surefire-reports"
    if directory.is_symlink() or not directory.is_dir():
        raise ValueError("a real test report directory is required")
    files = []
    for path in directory.glob("TEST-*.xml"):
        if len(files) >= 512:
            raise ValueError("test report count exceeds 512")
        files.append(path)
    files.sort()
    if not 1 <= len(files) <= 512:
        raise ValueError("test report count must be 1..512")
    reports = {}
    total = skipped = 0
    size = 0
    for path in files:
        sha = digest(path, 32 << 20)
        size += path.stat().st_size
        if size > 128 << 20:
            raise ValueError("test reports exceed 128 MiB")
        node = ET.parse(path).getroot()
        if node.tag != "testsuite":
            raise ValueError("a Surefire testsuite is required")
        counts = {key: int(node.attrib.get(key, "0")) for key in ("tests", "failures", "errors", "skipped")}
        if min(counts.values()) < 0 or counts["failures"] or counts["errors"] or counts["skipped"] > counts["tests"]:
            raise ValueError("negative, failing or invalid test counts")
        total += counts["tests"]
        skipped += counts["skipped"]
        reports[path.name] = sha
    if total <= skipped:
        raise ValueError("no executed test was reported")
    return {"tests": total, "skipped": skipped, "reports": reports,
            "log_sha256": digest(root / "test.log", 128 << 20)}


def main(args):
    mode, directory, source, contracts, jar, build = args
    if mode not in ("write", "verify"):
        raise ValueError("write or verify required")
    root = Path(directory)
    completion = digest(root / "complete.txt", 128)
    if (root / "complete.txt").read_text() != source + "\n":
        raise ValueError("full sequence did not complete for this source")
    expected = {"completion_sha256": completion, "source": source, "contracts_source": contracts, "contracts_jar_sha256": jar,
                "build_number": build, "status": "PASS", **evidence(root)}
    receipt = root / "receipt.json"
    if mode == "write":
        if receipt.exists():
            raise ValueError("receipt already exists")
        receipt.write_text(json.dumps(expected, sort_keys=True) + "\n")
    else:
        digest(receipt, 128 << 10)
        if json.loads(receipt.read_text()) != expected:
            raise ValueError("test receipt differs from this run's sources, artifact or evidence")


if __name__ == "__main__":
    main(sys.argv[1:])
