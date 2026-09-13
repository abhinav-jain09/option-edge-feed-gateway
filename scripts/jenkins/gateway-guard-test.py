#!/usr/bin/env python3
"""The gateway's own executed guard checks (the shared suites cover the guard, the validator and the
downstream-definition helper; this covers what only this repository's Jenkinsfiles do).

  * Codex gateway round 3, M1 — the reproductions against THIS repository's real Jenkinsfiles, each of which
    the round-3 validator accepted: both compatibility checks wrapped in `if (false) { … }`; the trigger
    stage's check wrapped in `catchError`; `post { always { sh 'kill 1234' } }` added to the host job; an
    effect stage's gate removed; and (Codex #1043 N3) the original `'\\.original$'` Groovy escape.
  * Artifact identity — the Image stage's shell block, EXECUTED with a sentinel docker and registry: the
    per-build tag is derived from BUILD_ID and PERMITTED_SHA (a caller IMAGE_TAG is refused), the lock digest
    is THIS build's push result (buildx metadata file keyed by BUILD_ID) and must equal what the registry
    serves for that tag, and a run that pushes nothing writes no lock.
Usage: gateway-guard-test.py
"""
from __future__ import annotations

import hashlib
import json
import os
import shutil
import stat
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
VALIDATOR = os.path.join(HERE, "validate-jenkinsfile-guard.py")
MANIFEST = os.path.join(HERE, "jenkins-permitted-sha-scope.txt")
DIGEST = "sha256:" + "d" * 64
OTHER = "sha256:" + "e" * 64


def read(name: str) -> str:
    with open(os.path.join(ROOT, name)) as fh:
        return fh.read()


def validate_mutated(name: str, text: str) -> subprocess.CompletedProcess:
    tmp = tempfile.mkdtemp()
    try:
        os.makedirs(os.path.join(tmp, "scripts/jenkins"))
        shutil.copy(os.path.join(HERE, "permitted-sha-guard.sh"), os.path.join(tmp, "scripts/jenkins/permitted-sha-guard.sh"))
        shutil.copy(MANIFEST, os.path.join(tmp, "scope.txt"))
        with open(os.path.join(tmp, name), "w") as fh:
            fh.write(text)
        return subprocess.run(["python3", VALIDATOR, "--root", tmp, "--manifest", os.path.join(tmp, "scope.txt"), "--only", name], capture_output=True, text=True)
    finally:
        shutil.rmtree(tmp, True)


def compat_blocks(text: str) -> list[str]:
    out = []
    i = 0
    while True:
        a = text.find("          def compat = sh(returnStatus: true, script: 'bash scripts/jenkins/require-guarded-downstream.sh service-deploy", i)
        if a < 0:
            return out
        b = text.index("          }\n", text.index("if (compat != 0) {", a)) + len("          }\n")
        out.append(text[a:b])
        i = b


def shell_body(text: str, anchor: str) -> str:
    i = text.index(anchor)
    s = text.index("sh '''\n", i) + len("sh '''\n")
    e = text.index("'''", s)
    return text[s:e].replace("\\\\", "\\")


def main() -> int:
    passed = failed = 0

    def check(name: str, ok: bool, detail: str = "") -> None:
        nonlocal passed, failed
        if ok:
            passed += 1
            print(f"ok   [{name}]")
        else:
            failed += 1
            print(f"FAIL [{name}]\n{detail}")

    jf = read("Jenkinsfile")
    jd = read("Jenkinsfile.deploy")
    r = validate_mutated("Jenkinsfile", jf)
    check("the real Jenkinsfile passes", r.returncode == 0, r.stdout)
    blocks = compat_blocks(jf)
    check("both compatibility checks are present", len(blocks) == 2, str(len(blocks)))
    m = jf
    for blk in blocks:
        m = m.replace(blk, "          if (false) {\n" + blk + "          }\n", 1)
    r = validate_mutated("Jenkinsfile", m)
    check("M1: both compatibility checks wrapped in if (false) are refused", r.returncode == 1 and "is not executably protected" in r.stdout, r.stdout)
    m = jf
    for blk in blocks:
        m = m.replace(blk, "          return\n" + blk, 1)
    m = m.replace("          build job: 'service-deploy',", "        }\n        script {\n          build job: 'service-deploy',", 1)
    r = validate_mutated("Jenkinsfile", m)
    check("M1 r4: return before each check, trigger in a later script block (Codex reproduction) is refused",
          r.returncode == 1 and "is not executably protected" in r.stdout, r.stdout)
    r = validate_mutated("Jenkinsfile", jf.replace(blocks[1], "          catchError(buildResult: 'FAILURE') {\n" + blocks[1] + "          }\n", 1))
    check("M1: the trigger stage's check wrapped in catchError is refused", r.returncode == 1 and "is not executably protected" in r.stdout, r.stdout)
    r = validate_mutated("Jenkinsfile", jf.replace("string(name: 'PERMITTED_SHA', value: params.DEPLOY_PERMITTED_SHA.trim())", "string(name: 'PERMITTED_SHA', value: dsha)", 1))
    check("the trigger forwarding a local instead of the judged parameter is refused", r.returncode == 1 and "forward params.<V>" in r.stdout, r.stdout)
    r = validate_mutated("Jenkinsfile.deploy", jd.rstrip().rstrip("}") + "  post { always { sh 'kill 1234' } }\n}\n")
    check("M1: an unconditional host-process kill in post{} is refused", r.returncode == 1 and "kill/pkill/killall (stops a process) is not inside the true branch" in r.stdout, r.stdout)
    r = validate_mutated("Jenkinsfile", jf.replace("grep -v '\\\\.original$'", "grep -v '\\.original$'", 1))
    check("N3: the single-backslash '\\.original$' Groovy escape is refused", r.returncode == 1 and "not a Groovy escape" in r.stdout, r.stdout)
    r = validate_mutated("Jenkinsfile", jf.replace("    stage('Image') {\n      when { expression { env.PERMITTED_SHA_GUARD == 'PASSED' } }\n", "    stage('Image') {\n", 1))
    check("an effect stage whose gate is removed is refused", r.returncode == 1 and "stage 'Image' after the guard has no `when` gate" in r.stdout, r.stdout)
    # The contracts guard is a DEDICATED step now (validator rule 9): its script is exactly the guard command.
    guard_line = next(l for l in jd.split("\n") if l.strip().startswith("sh 'PERMITTED_SHA=") and "permitted-sha-guard.sh --dir" in l)
    ind = guard_line[:len(guard_line) - len(guard_line.lstrip())]
    cmd = guard_line.strip()[len("sh '"):-1]
    for label, repl, say in [
        ("only echoed", ind + "sh 'echo " + cmd + "'", "is not re-bound"),
        ("with sh(script: ..., returnStatus: true) (Codex gateway M2 r5)", ind + "sh(script: '" + cmd + "', returnStatus: true)", "is not re-bound"),
        ("with a quoted 'returnStatus' key (Codex gateway M2 r6)", ind + "sh(script: '" + cmd + "', 'returnStatus': true)", "is not re-bound"),
        ("in a backtick substitution", ind + "sh 'out=`" + cmd + "` || true'", "is not re-bound"),
        ("after exit 0; exit 1 (Codex gateway I7)", ind + "sh 'exit 0; exit 1; " + cmd + "'", "is not re-bound"),
        ("under an EXIT trap (Codex gateway I8)", ind + "sh 'trap \\'exit 0\\' EXIT; " + cmd + "'", "is not re-bound"),
        ("back inside a shell block with || exit 1 (the old form)", ind + "sh " + chr(39) * 3 + "\n" + ind + "  set -eu\n" + ind + "  " + cmd + " || exit 1\n" + ind + chr(39) * 3, "is not re-bound"),
    ]:
        r = validate_mutated("Jenkinsfile.deploy", jd.replace(guard_line, repl, 1))
        check(f"the host job's contracts guard {label} is refused", r.returncode == 1 and say in r.stdout, r.stdout)
    # Codex gateway I9 / web M6-M7 on the real host job: the acquisition step and the guard are tied by RESOLVED path and
    # adjacency; the acquisition step carries nothing but the clone.
    acq_clone = next(l for l in jd.split("\n") if l.strip().startswith("git clone") and ".deps/options-edge-contracts" in l)
    r = validate_mutated("Jenkinsfile.deploy", jd.replace(acq_clone, acq_clone + "; mvn -B -f .deps/options-edge-contracts/pom.xml install", 1))
    check("M6: an effect on the contracts acquisition's own line (real Jenkinsfile.deploy) is refused", r.returncode == 1 and "no other command, separator or effect" in r.stdout, r.stdout)
    t0 = jd.index("        timeout(time: 10, unit: 'MINUTES') {\n          sh 'PERMITTED_SHA=")
    t1 = jd.index("        }\n", t0) + len("        }\n")
    tblock = jd[t0:t1]
    r = validate_mutated("Jenkinsfile.deploy", jd[:t0] + "        dir('other') {\n" + tblock.replace("\n        ", "\n          ").replace("        timeout", "          timeout", 1) + "        }\n" + jd[t1:])
    check("I9/M7: the contracts guard wrapped in dir('other') (real Jenkinsfile.deploy) is refused", r.returncode == 1 and "the guard resolves to 'other/.deps/options-edge-contracts'" in r.stdout, r.stdout)
    r = validate_mutated("Jenkinsfile.deploy", jd[:t1] + "        dir('other') {\n          sh " + chr(39) * 3 + "\n            git clone \"$CONTRACTS_REPO\" .deps/options-edge-contracts\n          " + chr(39) * 3 + "\n        }\n" + tblock + jd[t1:])
    check("I9: a second checkout into other/.deps/options-edge-contracts guarded as .deps/options-edge-contracts is refused", r.returncode == 1 and "the acquisition to 'other/.deps/options-edge-contracts'" in r.stdout, r.stdout)
    # Codex #1043 r8/r9 → runtime provenance verification. mvn install compiles the contracts source into the image,
    # so it must be preceded by the dedicated verify-permitted-tree step for the contracts checkout. Remove that step
    # and the definition is refused (its runtime behaviour — pull, reset, copy, archive-over-tree — is covered by
    # verify-permitted-tree-test.sh).
    for jname in ("Jenkinsfile.deploy", "Jenkinsfile"):
        src = jd if jname == "Jenkinsfile.deploy" else jf
        vstep = next(l for l in src.split("\n") if "verify-permitted-tree.sh --dir .deps/options-edge-contracts" in l)
        block = "        timeout(time: 10, unit: 'MINUTES') {\n" + vstep + "\n        }\n"
        assert block in src, jname
        r = validate_mutated(jname, src.replace(block, "", 1))
        check(f"provenance: contracts mvn install without a verify-permitted-tree step (real {jname}) is refused",
              r.returncode == 1 and "the nested checkout '.deps/options-edge-contracts'" in r.stdout, r.stdout)
    r = validate_mutated("Jenkinsfile.deploy", jd.replace(guard_line, ind + "script {\n" + ind + "  return\n" + guard_line + "\n" + ind + "}", 1))
    check("the host job's contracts guard behind an early return in its block (Codex gateway I6) is refused", r.returncode == 1 and ("can be skipped" in r.stdout or "is not re-bound" in r.stdout), r.stdout)

    # ---- artifact identity: the Image stage's shell, executed ----
    block = shell_body(jf, "stage('Image')")

    def world():
        tmp = tempfile.mkdtemp()
        ws = os.path.join(tmp, "ws")
        os.makedirs(os.path.join(ws, "scripts/jenkins"))
        os.makedirs(os.path.join(ws, "target"))
        subprocess.run(["git", "init", "-q", "-b", "main", ws], check=True)
        subprocess.run(["git", "-C", ws, "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-q", "--allow-empty", "-m", "g"], check=True)
        sha = subprocess.run(["git", "-C", ws, "rev-parse", "HEAD"], capture_output=True, text=True, check=True).stdout.strip()
        for f in ("permitted-sha-guard-version.sh", "resolve-pushed-digest.sh"):
            shutil.copy(os.path.join(HERE, f), os.path.join(ws, "scripts/jenkins", f))
        with open(os.path.join(ws, "scripts/jenkins/verify-embedded-contracts.sh"), "w") as fh:
            fh.write("#!/usr/bin/env bash\nexit 0\n")
        jar = os.path.join(ws, "target/options-edge-feed-gateway-1.0.jar")
        with open(jar, "wb") as fh:
            fh.write(b"jar")
        for n, v in ((".jar-sha256", hashlib.sha256(b"jar").hexdigest()), (".contracts-jar-sha256", "c" * 64), (".contracts-sha", "d" * 40)):
            with open(os.path.join(ws, n), "w") as fh:
                fh.write(v + "\n")
        b = os.path.join(tmp, "bin")
        os.makedirs(b)
        stubs = {
            "docker": "#!/usr/bin/env bash\n"
                      "if [ \"$1 $2\" = 'buildx build' ]; then\n"
                      "  while [ $# -gt 0 ]; do [ \"$1\" = --metadata-file ] && { [ -n \"$PUSH_DIGEST\" ] && printf '{\"containerimage.digest\": \"%s\"}' \"$PUSH_DIGEST\" > \"$2\" || echo '{}' > \"$2\"; }; shift; done\n"
                      "fi\nexit 0\n",
            "curl": "#!/usr/bin/env bash\nprintf 'HTTP/1.1 200 OK\\r\\nDocker-Content-Digest: %s\\r\\n\\r\\n' \"$REGISTRY_DIGEST\"\n",
            "sleep": "#!/usr/bin/env bash\nexit 0\n",
        }
        for n, t in stubs.items():
            p = os.path.join(b, n)
            with open(p, "w") as fh:
                fh.write(t)
            os.chmod(p, os.stat(p).st_mode | stat.S_IXUSR)
        return tmp, ws, sha

    def run(ws, tmp, sha, **over):
        e = {"PATH": f"{tmp}/bin:{os.environ['PATH']}", "HOME": tmp, "TMPDIR": tmp, "ENVIRONMENT": "dev", "PUSH_IMAGE": "true", "BUILD_ID": "57",
             "BUILD_NUMBER": "57", "BUILD_URL": "http://j/job/option-edge-feed-gateway/57/", "PERMITTED_SHA": sha, "IMAGE_REGISTRY": "localhost:5001",
             "PUSH_REGISTRY": "localhost:5001", "INSECURE_REGISTRIES": "", "DEV_IMAGE_TAG": "dev", "PERMITTED_SHA_GUARD_VERSION": "x",
             "PUSH_DIGEST": DIGEST, "REGISTRY_DIGEST": DIGEST}
        e.update(over)
        return subprocess.run(["bash", "-c", block], capture_output=True, text=True, env=e, cwd=ws)

    tmp, ws, sha = world()
    try:
        r = run(ws, tmp, sha)
        lock_path = os.path.join(ws, ".jenkins-tmp/image-lock-57.env")
        lock = open(lock_path).read() if os.path.exists(lock_path) else ""
        check("push: the lock is THIS build's (BUILD_ID-keyed, derived tag, pushed digest)", r.returncode == 0
              and "OPTIONS_EDGE_IMAGE_LOCK_BUILD_ID=57\n" in lock and f"FEED_GATEWAY_IMAGE=localhost:5001/options-edge-feed-gateway:57-{sha[:12]}@{DIGEST}\n" in lock
              and open(os.path.join(ws, ".jenkins-tmp/required-image-57")).read().strip() == f"localhost:5001/options-edge-feed-gateway:57-{sha[:12]}@{DIGEST}", r.stdout + r.stderr)
        r = run(ws, tmp, sha, BUILD_ID="58", BUILD_NUMBER="58", REGISTRY_DIGEST=OTHER)
        check("push: a registry serving another digest for the build tag is refused, no lock", r.returncode != 0 and "this build pushed" in r.stderr
              and not os.path.exists(os.path.join(ws, ".jenkins-tmp/image-lock-58.env")), r.stdout + r.stderr)
        r = run(ws, tmp, sha, BUILD_ID="59", BUILD_NUMBER="59", PUSH_DIGEST="")
        check("push: a push that reports no digest is refused, no lock", r.returncode != 0 and "reported no image digest" in r.stderr
              and not os.path.exists(os.path.join(ws, ".jenkins-tmp/image-lock-59.env")), r.stdout + r.stderr)
        r = run(ws, tmp, sha, BUILD_ID="60", BUILD_NUMBER="60", IMAGE_TAG="shared-release")
        check("I4 sibling: a caller-supplied IMAGE_TAG is refused before anything is built", r.returncode != 0 and "IMAGE_TAG='shared-release' is refused" in r.stderr, r.stdout + r.stderr)
        r = run(ws, tmp, "f" * 40, BUILD_ID="61", BUILD_NUMBER="61")
        check("a HEAD other than PERMITTED_SHA is refused before tagging", r.returncode != 0 and "HEAD is not PERMITTED_SHA" in r.stderr, r.stdout + r.stderr)
        r = run(ws, tmp, sha, BUILD_ID="62", BUILD_NUMBER="62", PUSH_IMAGE="false")
        check("no push: no lock and no required image for this build", r.returncode == 0 and not os.path.exists(os.path.join(ws, ".jenkins-tmp/image-lock-62.env"))
              and not os.path.exists(os.path.join(ws, ".jenkins-tmp/required-image-62")), r.stdout + r.stderr)
    finally:
        shutil.rmtree(tmp, True)
    g = jf[jf.index("// Only THIS build's lock (keyed by BUILD_ID"):jf.index("end stage('Build')")]
    check("Groovy publishes and forwards only the BUILD_ID-keyed lock", 'fileExists(".jenkins-tmp/image-lock-${env.BUILD_ID}.env")' in g
          and 'readFile(".jenkins-tmp/required-image-${env.BUILD_ID}")' in g and "readFile('.jenkins-tmp/required-image')" not in jf, g)

    print(f"gateway-guard-test: {passed} passed, {failed} failed")
    if failed == 0:
        print("gateway-guard-test: ALL PASS")
        return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
