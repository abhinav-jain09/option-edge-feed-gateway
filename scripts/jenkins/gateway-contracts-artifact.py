#!/usr/bin/env python3
"""Consume one reviewed, archived Jenkins release; never rebuild or guess a contracts coordinate."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import tempfile
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
POM_ENTRY = "META-INF/maven/com.optionsedge/options-edge-contracts/pom.xml"
SOURCE_REPO = "git@github.com:abhinav-jain09/options-edge-contracts.git"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate contracts metadata key")
        result[key] = value
    return result


def parsed(data, cap):
    if len(data) > cap:
        raise ValueError("contracts metadata capacity")
    return json.loads(data, object_pairs_hook=unique)


def bounded_file(path, cap):
    with path.open("rb") as source:
        raw = source.read(cap + 1)
    if len(raw) > cap:
        raise ValueError("contracts file capacity")
    return raw


def gav(data):
    if len(data) > 262_144:
        raise ValueError("contracts POM capacity")
    root = ET.fromstring(data)
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    return tuple(root.findtext("m:" + name, namespaces=ns) for name in ("groupId", "artifactId", "version"))


def load_lock(root):
    lock = parsed(bounded_file(root / "ci/contracts-release.json", 8192), 8192)
    keys = {"schemaVersion", "groupId", "artifactId", "version", "sourceSha", "jenkinsJob", "jenkinsBuild", "artifactPath", "bytes", "jarSha256", "pomSha256"}
    if set(lock) != keys or type(lock["schemaVersion"]) is not int or lock["schemaVersion"] != 1:
        raise ValueError("contracts lock shape")
    if lock["groupId"] != "com.optionsedge" or lock["artifactId"] != "options-edge-contracts" or lock["jenkinsJob"] != "options-edge-contracts":
        raise ValueError("contracts release identity")
    if not isinstance(lock["version"], str) or not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", lock["version"]):
        raise ValueError("contracts release version")
    if any(not isinstance(lock[k], str) or not re.fullmatch(r"[0-9a-f]{64}", lock[k]) for k in ("jarSha256", "pomSha256")):
        raise ValueError("contracts digest")
    if not isinstance(lock["sourceSha"], str) or not re.fullmatch(r"[0-9a-f]{40}", lock["sourceSha"]):
        raise ValueError("contracts source")
    if type(lock["jenkinsBuild"]) is not int or not 0 < lock["jenkinsBuild"] < 1_000_000_000 or type(lock["bytes"]) is not int or not 0 < lock["bytes"] <= 64 * 1024 * 1024:
        raise ValueError("contracts release capacity")
    if lock["artifactPath"] != "target/options-edge-contracts-" + lock["version"] + ".jar":
        raise ValueError("contracts artifact path")
    tree = ET.parse(root / "pom.xml")
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    pins = [node.findtext("m:version", namespaces=ns) for node in tree.findall("m:dependencies/m:dependency", ns)
            if node.findtext("m:groupId", namespaces=ns) == lock["groupId"] and node.findtext("m:artifactId", namespaces=ns) == lock["artifactId"]]
    resolved=[]
    for pin in pins:
        if pin == '${options-edge-contracts.version}':
            values=tree.findall('m:properties/m:options-edge-contracts.version', ns)
            if len(values)!=1: raise ValueError('contracts property identity')
            pin=values[0].text
        resolved.append(pin)
    if resolved != [lock["version"]]:
        raise ValueError("gateway dependency and release lock differ")
    return lock


def artifact_origin(env):
    return env.get("JENKINS_ARTIFACT_BASE_URL") or env.get("JENKINS_URL", "")


def fetch(url, cap):
    deadline = time.monotonic() + 120
    with urllib.request.urlopen(url, timeout=15) as response:
        out = bytearray()
        while True:
            if time.monotonic() >= deadline:
                raise ValueError("contracts download deadline")
            chunk = response.read(min(65536, cap + 1 - len(out)))
            if not chunk:
                return bytes(out)
            out.extend(chunk)
            if len(out) > cap:
                raise ValueError("contracts download capacity")


def validate_release(metadata, lock):
    if metadata.get("result") != "SUCCESS" or metadata.get("keepLog") is not True:
        raise ValueError("contracts Jenkins release is not successful and retained")
    sources = [(action.get("lastBuiltRevision") or {}).get("SHA1") for action in metadata.get("actions", [])
               if isinstance(action,dict) and SOURCE_REPO in (action.get("remoteUrls") or [])]
    if sources != [lock["sourceSha"]]:
        raise ValueError("contracts Jenkins checkout differs")


def validate_jar(raw, lock):
    if len(raw) != lock["bytes"] or sha(raw) != lock["jarSha256"]:
        raise ValueError("contracts jar content differs")
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        matches = [entry for entry in archive.infolist() if entry.filename == POM_ENTRY]
        if len(matches) != 1 or matches[0].file_size > 262_144:
            raise ValueError("contracts embedded POM capacity or identity")
        pom = archive.read(matches[0])
    if sha(pom) != lock["pomSha256"] or gav(pom) != (lock["groupId"], lock["artifactId"], lock["version"]):
        raise ValueError("contracts embedded POM differs")
    return pom


def store(path, raw, replace=False):
    if path.exists() and not replace:
        if bounded_file(path, len(raw)) != raw:
            raise ValueError("contracts immutable cache differs")
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    name = None
    try:
        with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as output:
            name = output.name
            output.write(raw)
        if replace:
            os.replace(name, path)
            name = None
        else:
            try:
                os.link(name, path)
            except FileExistsError:
                if bounded_file(path, len(raw)) != raw:
                    raise ValueError("contracts immutable cache race")
    finally:
        if name:
            Path(name).unlink(missing_ok=True)


def paths(root, lock):
    cache = root / ".jenkins-tmp/contracts-release"
    stem = "options-edge-contracts-" + lock["version"]
    installed = root / ".m2/repository/com/optionsedge/options-edge-contracts" / lock["version"]
    return cache / (stem + ".jar"), cache / (stem + ".pom"), installed / (stem + ".jar"), installed / (stem + ".pom")


def prepare(root, base, reader=fetch):
    lock = load_lock(root)
    uri = urllib.parse.urlparse(base)
    if uri.scheme not in ("http", "https") or not uri.hostname or uri.username or uri.password or uri.query or uri.fragment:
        raise ValueError("contracts Jenkins origin")
    release = base.rstrip("/") + "/job/options-edge-contracts/" + str(lock["jenkinsBuild"]) + "/"
    metadata = parsed(reader(release + "api/json?tree=result,keepLog,actions[lastBuiltRevision[SHA1],remoteUrls]", 524_288), 524_288)
    validate_release(metadata, lock)
    jar, pom, _, _ = paths(root, lock)
    raw = bounded_file(jar, lock["bytes"]) if jar.exists() else reader(release + "artifact/" + lock["artifactPath"], lock["bytes"])
    content = validate_jar(raw, lock)
    store(jar, raw)
    store(pom, content)
    store(jar.parent / "current.jar", raw, replace=True)
    store(jar.parent / "current.pom", content, replace=True)
    receipt = {"release": lock, "releaseValidated": True, "jarSha256": sha(raw), "pomSha256": sha(content),
               "gatewayBuildId": os.environ.get("BUILD_ID"), "gatewaySource": os.environ.get("PERMITTED_SHA")}
    (jar.parent / "provenance.json").write_text(json.dumps(receipt, indent=2) + "\n")
    return receipt


def verify_installed(root):
    lock = load_lock(root)
    _, _, jar, pom = paths(root, lock)
    if sha(bounded_file(jar, lock["bytes"])) != lock["jarSha256"] or sha(bounded_file(pom, 262_144)) != lock["pomSha256"]:
        raise ValueError("installed contracts content differs from reviewed release")
    return lock


def permitted_lock(root, permitted_sha):
    lock = load_lock(root)
    if permitted_sha != lock['sourceSha']:
        raise ValueError('permitted contracts source differs from reviewed release')
    return lock


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("prepare", "verify-installed"))
    args = parser.parse_args()
    permitted_lock(ROOT, os.environ.get('CONTRACTS_PERMITTED_SHA'))
    if args.mode == "prepare":
        result = prepare(ROOT, artifact_origin(os.environ))["release"]
    else:
        result = verify_installed(ROOT)
    print("contracts release verified", result["version"], result["sourceSha"], result["jarSha256"])
