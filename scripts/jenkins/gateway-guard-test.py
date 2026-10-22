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
  * The Jib engine (a push to a plain-http registry, no Docker daemon): the same stage executed with a docker that fails if called and a
    Jib stand-in — engine choice, platform/labels/tags handed to `mvn package -Pjib-image`, the digest file as the only source of the
    lock's digest (a stale one is never read), and the validator's refusals of a Jib step without its primary verify.
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
        # the whole scripts/ tree: the validator follows every repository script a step runs
        shutil.copytree(os.path.join(ROOT, "scripts"), os.path.join(tmp, "scripts"), ignore=shutil.ignore_patterns("__pycache__"))
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
        ind = vstep[:len(vstep) - len(vstep.lstrip())][:-2]
        block = ind + "timeout(time: 10, unit: 'MINUTES') {\n" + vstep + "\n" + ind + "}\n"
        assert block in src, jname
        r = validate_mutated(jname, src.replace(block, "", 1))
        check(f"provenance: contracts mvn install without a verify-permitted-tree step (real {jname}) is refused",
              r.returncode == 1 and "the nested checkout '.deps/options-edge-contracts'" in r.stdout, r.stdout)

    # Codex gateway r11 I10 (the class): statement adjacency protected the statement boundary, not the commands INSIDE the
    # consuming step. Each effect is now a DEDICATED step whose whole body is one command matched against its template;
    # a source change inside that step, however spelled, is refused — on both real files.
    tq = chr(39) * 3
    install = "sh 'mvn -B -f .deps/options-edge-contracts/pom.xml install'"
    # Derived, not pinned (same precedent as `image` below): the package step now carries
    # -Doptions-edge-contracts.version so it builds against the contracts this pipeline installed,
    # and a literal here would silently assert itself out of existence on the next such change.
    # The template itself is enforced by the validator; this fixture only needs the real command
    # to mutate, so it reads it from the file under test.
    package = next(l.strip() for l in jf.split("\n") if l.strip().startswith("sh 'mvn -B -DskipTests clean package"))
    image = next(l.strip() for l in jf.split("\n") if l.strip().startswith("sh 'docker buildx build ") and '-t "${IMAGE_REF_2}"' not in l)
    for jname, src, old, new, say in [
        ("Jenkinsfile", jf, install, "sh 'cp -r /tmp/other/src .deps/options-edge-contracts/ && mvn -B -f .deps/options-edge-contracts/pom.xml install'", "does not fit the fixed `mvn` template"),
        ("Jenkinsfile", jf, install, "sh " + tq + "\n              git -C .deps/options-edge-contracts apply /tmp/p.diff\n              mvn -B -f .deps/options-edge-contracts/pom.xml install\n            " + tq, "is not a DEDICATED effect step"),
        ("Jenkinsfile", jf, install, "sh 'mvn -B -f .deps/options-edge-contracts/pom.xml versions:set -DnewVersion=9 install'", "is not in the template's goal list"),
        ("Jenkinsfile", jf, package, "sh 'mvn -B package -Dmaven.repo.local=/tmp/other; true'", "does not fit the fixed `mvn` template"),
        ("Jenkinsfile", jf, package, "sh " + tq + "\n              export MAVEN_OPTS=-Dx\n              mvn -B package\n            " + tq, "is not a DEDICATED effect step"),
        ("Jenkinsfile", jf, image, image.replace("--no-cache ", "--no-cache --build-context extra=/tmp/other "), "is not an option of the docker build template"),
        ("Jenkinsfile", jf, image, image.replace(" .'", " \"${CTX}\"'"), "the build context must be a literal"),
        ("Jenkinsfile", jf, image, "sh 'cp /tmp/other.jar target/ && " + image[len("sh '"):], "does not fit the fixed `docker` template"),
        ("Jenkinsfile.deploy", jd, "sh 'mvn -B clean package -DskipTests'", "sh 'rm -rf src && mvn -B clean package -DskipTests'", "does not fit the fixed `mvn` template"),
        ("Jenkinsfile.deploy", jd, "sh 'mvn -B -f .deps/options-edge-contracts/pom.xml install -DskipTests'", "sh 'mvn -B -f .deps/options-edge-contracts/pom.xml install -DskipTests > /tmp/log'", "does not fit the fixed `mvn` template"),
    ]:
        assert src.count(old) >= 1, (jname, old)
        r = validate_mutated(jname, src.replace(old, new, 1))
        check(f"I10 class: the effect step's own body — {new[:70]!r} in {jname} is refused", r.returncode == 1 and say in r.stdout, r.stdout)
    # web M10 on this file: the image context '.' holds the contracts clone too, so the build needs BOTH verifies
    # The Image stage has two engines (Jib for a push to a plain-http registry, docker otherwise), each with a one-tag and a two-tag
    # branch; the mutations below judge each engine's branches separately.
    cut = jf.index("            } else {\n            try {")
    jib_part, docker_part = jf[:cut], jf[cut:]
    cblock = next(l for l in docker_part.split("\n") if "verify-permitted-tree.sh --dir .deps/options-edge-contracts" in l and l.startswith("                  "))
    ind = cblock[:len(cblock) - len(cblock.lstrip())][:-2]
    cb = ind + "timeout(time: 10, unit: 'MINUTES') {\n" + cblock + "\n" + ind + "}\n"
    assert docker_part.count(cb) == 2, docker_part.count(cb)
    r = validate_mutated("Jenkinsfile", jib_part + docker_part.replace(cb, "", 1))
    check("M10: the image build of context '.' without the contracts-clone verify is refused", r.returncode == 1 and "consumes a path inside the nested checkout '.deps/options-edge-contracts'" in r.stdout, r.stdout)
    pblock_line = next(l for l in docker_part.split("\n") if "verify-permitted-tree.sh --dir . " in l and l.startswith("                  "))
    pb = ind + "timeout(time: 10, unit: 'MINUTES') {\n" + pblock_line + "\n" + ind + "}\n"
    r = validate_mutated("Jenkinsfile", jib_part + docker_part.replace(pb, "", 1))
    check("M10: the image build without the primary verify is refused", r.returncode == 1 and "the primary checkout '.'" in r.stdout, r.stdout)
    # The Jib branches: `mvn package -Pjib-image` is a source-consuming effect of the primary workspace (it reads ci/jib-image/pom.xml
    # and the staged jar under .jenkins-tmp), so it needs the primary verify immediately before it, in BOTH of its branches.
    jline = next(l for l in jib_part.split("\n") if "verify-permitted-tree.sh --dir . " in l and "ci/jib-image/target" in l)
    jind = jline[:len(jline) - len(jline.lstrip())][:-2]
    jb = jind + "timeout(time: 10, unit: 'MINUTES') {\n" + jline + "\n" + jind + "}\n"
    assert jib_part.count(jb) == 2, jib_part.count(jb)
    for which in (0, 1):
        i = [m.start() for m in __import__("re").finditer(__import__("re").escape(jb), jf)][which]
        r = validate_mutated("Jenkinsfile", jf[:i] + jf[i + len(jb):])
        check(f"Jib: the `mvn package -Pjib-image` step without the primary verify is refused (branch {which + 1})", r.returncode == 1 and "the primary checkout '.'" in r.stdout, r.stdout)
    jmvn = [l.strip() for l in jib_part.split("\n") if l.strip().startswith("sh 'mvn -B -q -f ci/jib-image/pom.xml package -Pjib-image")]
    assert len(jmvn) == 2 and all(" -Pjib-image " in l for l in jmvn), jmvn
    jm_tag = next(l for l in jmvn if "JIB_EXTRA_TAGS" in l)
    for jm in jmvn:
        for label, repl, say in [
            ("a second command", "sh 'cp /tmp/other.jar .jenkins-tmp/jib-options-edge-feed-gateway/app/app.jar && " + jm[len("sh '"):], "does not fit the fixed `mvn` template"),
            ("a command after it", jm[:-1] + "; true'", "does not fit the fixed `mvn` template"),
            ("a goal that publishes through another plugin", jm.replace(" package ", " package deploy:deploy ", 1), "is not in the template's goal list"),
            ("a pom outside the workspace", jm.replace("-f ci/jib-image/pom.xml", "-f /tmp/other/pom.xml", 1), "must name a literal repository path"),
        ]:
            r = validate_mutated("Jenkinsfile", jf.replace(jm, repl, 1))
            check(f"Jib: the effect step with {label} is refused", r.returncode == 1 and say in r.stdout, r.stdout)
        # a step between the verify and the effect breaks the chain
        r = validate_mutated("Jenkinsfile", jf.replace(jm, "sh 'cp /tmp/other.jar .jenkins-tmp/jib-options-edge-feed-gateway/app/app.jar'\n                  " + jm, 1))
        check("Jib: a step between the verify and the `mvn package -Pjib-image` step is refused", r.returncode == 1 and "the primary checkout '.'" in r.stdout, r.stdout)
    r = validate_mutated("Jenkinsfile.deploy", jd.replace(guard_line, ind + "script {\n" + ind + "  return\n" + guard_line + "\n" + ind + "}", 1))
    check("the host job's contracts guard behind an early return in its block (Codex gateway I6) is refused", r.returncode == 1 and ("can be skipped" in r.stdout or "is not re-bound" in r.stdout), r.stdout)

    # Codex N4: the byproducts the build writes into the workspace root (.contracts-version, .contracts-jar-sha256,
    # .jar-sha256, the workspace maven cache) must be ignored, so the mandatory footprint gate's `git status
    # --porcelain` (and any workspace verify) still sees a clean tree. Check out HEAD, apply this change's .gitignore,
    # create those byproducts, and assert git status is empty.
    tmp = tempfile.mkdtemp()
    co = os.path.join(tmp, "co")
    subprocess.run(["git", "-C", ROOT, "worktree", "add", "--detach", co, "HEAD"], capture_output=True)
    try:
        shutil.copy(os.path.join(ROOT, ".gitignore"), os.path.join(co, ".gitignore"))
        subprocess.run(["git", "-C", co, "add", ".gitignore"], capture_output=True)
        subprocess.run(["git", "-C", co, "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-q", "-m", "i"], capture_output=True)
        for rel, body in [(".contracts-sha", "a\n"), (".contracts-version", "0.2.0\n"), (".contracts-jar-sha256", "d\n"),
                          (".jar-sha256", "e\n"), (".m2/repository/x.jar", "j\n"), ("target/x.jar", "j\n"), (".jenkins-tmp/l", "l\n"),
                          ("ci/jib-image/target/jib-image.digest", "d\n")]:
            p = os.path.join(co, rel)
            os.makedirs(os.path.dirname(p), exist_ok=True)
            open(p, "w").write(body)
        st = subprocess.run(["git", "-C", co, "status", "--porcelain"], capture_output=True, text=True).stdout.strip()
        check("N4: the build's root byproducts are gitignored, so the footprint gate sees a clean tree", st == "", f"git status not empty:\n{st}")
        os.remove(os.path.join(co, "ci/jib-image/target/jib-image.digest"))   # only a Jib build writes it; the docker-engine verifies below must not see it
        # the primary verify in front of `mvn package` and the image build must ACCEPT exactly what this build writes (its
        # --allow-ignored list parsed from the real Jenkinsfile), with the contracts clone and Python bytecode present too
        for rel, body in [(".deps/options-edge-contracts/pom.xml", "<project/>\n"), (".jenkins-tmp/java-home", "/x\n"), ("scripts/__pycache__/m.pyc", "c")]:
            p = os.path.join(co, rel)
            os.makedirs(os.path.dirname(p), exist_ok=True)
            open(p, "w").write(body)
        vline = next(l for l in jf.split("\n") if "verify-permitted-tree.sh --dir . " in l and "ci/jib-image/target" not in l)
        allow = vline.split("verify-permitted-tree.sh --dir . ", 1)[1].rstrip("'").split()
        head = subprocess.run(["git", "-C", co, "rev-parse", "HEAD"], capture_output=True, text=True).stdout.strip()
        r = subprocess.run(["bash", os.path.join(HERE, "verify-permitted-tree.sh"), "--dir", co] + allow, capture_output=True, text=True,
                           env={**os.environ, "PERMITTED_SHA": head})
        check("the primary verify accepts the real post-build workspace (declared byproducts only)", r.returncode == 0, r.stdout + r.stderr)
        # the verify in front of the Jib step must accept the Jib build's own output (ci/jib-image/target) and the staged jar, and the
        # SAME workspace without --allow-ignored ci/jib-image/target must not (the declaration is what admits it)
        jvl = next(l for l in jf.split("\n") if "verify-permitted-tree.sh --dir . " in l and "ci/jib-image/target" in l)
        jallow = jvl.split("verify-permitted-tree.sh --dir . ", 1)[1].rstrip("'").split()
        os.makedirs(os.path.join(co, "ci/jib-image/target"), exist_ok=True)
        open(os.path.join(co, "ci/jib-image/target/jib-image.digest"), "w").write("d\n")
        os.makedirs(os.path.join(co, ".jenkins-tmp/jib-options-edge-feed-gateway/app"), exist_ok=True)
        open(os.path.join(co, ".jenkins-tmp/jib-options-edge-feed-gateway/app/app.jar"), "w").write("j")
        r = subprocess.run(["bash", os.path.join(HERE, "verify-permitted-tree.sh"), "--dir", co] + jallow, capture_output=True, text=True, env={**os.environ, "PERMITTED_SHA": head})
        check("the Jib step's primary verify accepts the Jib build's own output and the staged jar", r.returncode == 0, r.stdout + r.stderr)
        r = subprocess.run(["bash", os.path.join(HERE, "verify-permitted-tree.sh"), "--dir", co] + [a for a in allow], capture_output=True, text=True, env={**os.environ, "PERMITTED_SHA": head})
        check("without --allow-ignored ci/jib-image/target the same workspace is refused", r.returncode != 0 and "ci/jib-image/target" in r.stderr, r.stdout + r.stderr)
        open(os.path.join(co, "src-extra.txt"), "w").write("x")
        r = subprocess.run(["bash", os.path.join(HERE, "verify-permitted-tree.sh"), "--dir", co] + allow, capture_output=True, text=True,
                           env={**os.environ, "PERMITTED_SHA": head})
        check("the same workspace with one untracked file added is refused", r.returncode != 0 and "differs from the permitted commit" in r.stderr, r.stdout + r.stderr)
    finally:
        subprocess.run(["git", "-C", ROOT, "worktree", "remove", "--force", co], capture_output=True)
        shutil.rmtree(tmp, True)

    # ---- artifact identity: the Image stage, executed step by step: prepare -> the DEDICATED build step (its env from
    # the prepare step's file, as withEnv hands it) -> lock ----
    stage_txt = jf[jf.index("stage('Image')"):jf.index("// Only THIS build's lock (keyed by BUILD_ID")]
    prep_block = shell_body(stage_txt, "Image: prepare")
    lock_block = shell_body(stage_txt, "Image: lock")
    two_tag_line = next(l.strip() for l in stage_txt.split("\n") if l.strip().startswith("sh 'docker buildx build ") and '"${IMAGE_REF_2}"' in l)
    one_tag_line = next(l.strip() for l in stage_txt.split("\n") if l.strip().startswith("sh 'docker buildx build ") and '"${IMAGE_REF_2}"' not in l)
    block = "\n".join([
        "set -e",
        "( " + prep_block + " )",
        "while IFS= read -r kv; do [ -n \"$kv\" ] && export \"$kv\"; done < \".jenkins-tmp/image-env-$BUILD_ID\"",
        "if [ -n \"$IMAGE_REF_2\" ]; then " + two_tag_line[len("sh '"):-1] + "; else " + one_tag_line[len("sh '"):-1] + "; fi",
        "( " + lock_block + " )",
    ])

    def world():
        tmp = tempfile.mkdtemp()
        ws = os.path.join(tmp, "ws")
        os.makedirs(os.path.join(ws, "scripts/jenkins"))
        os.makedirs(os.path.join(ws, "target"))
        subprocess.run(["git", "init", "-q", "-b", "main", ws], check=True)
        subprocess.run(["git", "-C", ws, "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-q", "--allow-empty", "-m", "g"], check=True)
        sha = subprocess.run(["git", "-C", ws, "rev-parse", "HEAD"], capture_output=True, text=True, check=True).stdout.strip()
        for f in ("permitted-sha-guard-version.sh", "resolve-pushed-digest.sh", "write-jib-metadata.sh"):
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
            # Jib stand-in: records its argv, and (like the real plugin) writes the digest it "pushed" under the pom's target/
            "mvn": "#!/usr/bin/env bash\nprintf '%s\\n' \"$*\" >> \"$MVN_LOG\"\n"
                   "[ -z \"$JIB_DIGEST\" ] || { mkdir -p ci/jib-image/target && printf '%s\\n' \"$JIB_DIGEST\" > ci/jib-image/target/jib-image.digest; }\nexit 0\n",
        }
        for n, t in stubs.items():
            p = os.path.join(b, n)
            with open(p, "w") as fh:
                fh.write(t)
            os.chmod(p, os.stat(p).st_mode | stat.S_IXUSR)
        return tmp, ws, sha

    def run(ws, tmp, sha, block=block, **over):
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

    # ---- the Jib engine (a push to a plain-http registry): the same stage, executed with a docker that FAILS if it is ever called ----
    jib_lines = [l.strip() for l in stage_txt.split("\n") if l.strip().startswith("sh 'mvn -B -q -f ci/jib-image/pom.xml package -Pjib-image")]
    jib_two = next(l for l in jib_lines if "JIB_EXTRA_TAGS" in l)
    jib_one = next(l for l in jib_lines if "JIB_EXTRA_TAGS" not in l)
    jib_block = "\n".join([
        "set -e",
        "( " + prep_block + " )",
        "while IFS= read -r kv; do [ -n \"$kv\" ] && export \"$kv\"; done < \".jenkins-tmp/image-env-$BUILD_ID\"",
        "[ \"$IMAGE_ENGINE\" = jib ] || { echo \"engine is $IMAGE_ENGINE\" >&2; exit 41; }",
        "if [ -n \"$IMAGE_REF_2\" ]; then " + jib_two[len("sh '"):-1] + "; else " + jib_one[len("sh '"):-1] + "; fi",
        "( " + lock_block + " )",
    ])
    GV = "a" * 64

    def jib_run(ws, tmp, sha, block=jib_block, **over):
        with open(os.path.join(tmp, "bin/docker"), "w") as fh:
            fh.write("#!/usr/bin/env bash\necho \"docker $*\" >> \"$DOCKER_LOG\"\nexit 99\n")
        e = {"PERMITTED_SHA_GUARD_VERSION": GV, "INSECURE_REGISTRIES": "localhost:5001 192.168.100.252:5000", "DOCKER_LOG": os.path.join(tmp, "docker.log"),
             "MVN_LOG": os.path.join(tmp, "mvn.log"), "JIB_DIGEST": DIGEST, "WORKSPACE": ws, "BUILD_PLATFORM": "linux/arm64"}
        e.update(over)
        return run(ws, tmp, sha, block=block, **e)

    tmp, ws, sha = world()
    try:
        r = jib_run(ws, tmp, sha)
        lock_path = os.path.join(ws, ".jenkins-tmp/image-lock-57.env")
        lock = open(lock_path).read() if os.path.exists(lock_path) else ""
        margs = open(os.path.join(tmp, "mvn.log")).read() if os.path.exists(os.path.join(tmp, "mvn.log")) else ""
        check("Jib push (dev): no docker command runs at all, the lock is THIS build's with the digest Jib pushed", r.returncode == 0
              and not os.path.exists(os.path.join(tmp, "docker.log")) and f"FEED_GATEWAY_IMAGE=localhost:5001/options-edge-feed-gateway:57-{sha[:12]}@{DIGEST}\n" in lock
              and "OPTIONS_EDGE_IMAGE_LOCK_PLATFORM=linux/arm64\n" in lock, r.stdout + r.stderr + margs)
        check("Jib push (dev): both tags, the platform, all five provenance labels and the staged jar reach Jib exactly",
              f"-Djib.to.image=localhost:5001/options-edge-feed-gateway:57-{sha[:12]}" in margs and "-Djib.to.tags=dev" in margs and f"-Djib.jar.sha256={hashlib.sha256(b'jar').hexdigest()}" in margs and "-Djib.from.platforms=linux/arm64" in margs
              and f"-Djib.extraDirectories.paths={ws}/.jenkins-tmp/jib-options-edge-feed-gateway" in margs and "-Pjib-image" in margs
              and f"-Djib.container.labels=options-edge.contracts-revision={'d' * 40},options-edge.source-revision={sha},options-edge.jar-sha256={hashlib.sha256(b'jar').hexdigest()},"
                  f"options-edge.contracts-jar-sha256={'c' * 64},options-edge.guard-version={GV}" in margs, margs)
        check("Jib push (dev): the verified jar is staged as /app/app.jar's source", open(os.path.join(ws, ".jenkins-tmp/jib-options-edge-feed-gateway/app/app.jar"), "rb").read() == b"jar")
        meta = json.load(open(os.path.join(ws, ".jenkins-tmp/push-metadata-57.json")))
        check("Jib push: the metadata file the lock reads is buildx-shaped", meta == {"containerimage.digest": DIGEST}, str(meta))
        # production: amd64, the per-build prod- tag and the moving :prod tag
        os.remove(os.path.join(tmp, "mvn.log"))
        r = jib_run(ws, tmp, sha, BUILD_ID="63", BUILD_NUMBER="63", ENVIRONMENT="production", BUILD_PLATFORM="linux/amd64", IMAGE_REGISTRY="192.168.100.252:5000", PUSH_REGISTRY="192.168.100.252:5000")
        margs = open(os.path.join(tmp, "mvn.log")).read() if os.path.exists(os.path.join(tmp, "mvn.log")) else ""
        check("Jib push (production): linux/amd64, prod-<build>-<sha> and the moving :prod tag", r.returncode == 0 and "-Djib.from.platforms=linux/amd64" in margs and "-Djib.to.tags=prod" in margs
              and f"-Djib.to.image=192.168.100.252:5000/options-edge-feed-gateway:prod-63-{sha[:12]}" in margs and not os.path.exists(os.path.join(tmp, "docker.log")), r.stdout + r.stderr + margs)
        # a push with no moving tag uses the one-tag branch and passes no tags
        os.remove(os.path.join(tmp, "mvn.log"))
        r = jib_run(ws, tmp, sha, BUILD_ID="64", BUILD_NUMBER="64", DEV_IMAGE_TAG="")
        margs = open(os.path.join(tmp, "mvn.log")).read() if os.path.exists(os.path.join(tmp, "mvn.log")) else ""
        check("Jib push without a moving tag: the one-tag step passes no -Djib.to.tags", r.returncode == 0 and "jib.to.tags" not in margs, r.stdout + r.stderr + margs)
        # refusals
        stale = os.path.exists(os.path.join(ws, "ci/jib-image/target/jib-image.digest"))   # the previous build's digest is still there
        r = jib_run(ws, tmp, sha, BUILD_ID="65", BUILD_NUMBER="65", JIB_DIGEST="")
        check("Jib push that writes no digest file is refused, no lock", r.returncode != 0 and "jib-image.digest" in r.stderr
              and not os.path.exists(os.path.join(ws, ".jenkins-tmp/image-lock-65.env")), r.stdout + r.stderr)
        check("Jib: a digest file left by an EARLIER build is never read as this build's", stale and r.returncode != 0, r.stdout + r.stderr)
        r = jib_run(ws, tmp, sha, BUILD_ID="66", BUILD_NUMBER="66", JIB_DIGEST="sha256:not-a-digest")
        check("Jib push that reports a malformed digest is refused, no lock", r.returncode != 0 and "reported no image digest" in r.stderr
              and not os.path.exists(os.path.join(ws, ".jenkins-tmp/image-lock-66.env")), r.stdout + r.stderr)
        r = jib_run(ws, tmp, sha, BUILD_ID="67", BUILD_NUMBER="67", REGISTRY_DIGEST=OTHER)
        check("Jib push: a registry serving another digest for the build tag is refused, no lock", r.returncode != 0 and "this build pushed" in r.stderr
              and not os.path.exists(os.path.join(ws, ".jenkins-tmp/image-lock-67.env")), r.stdout + r.stderr)
        r = jib_run(ws, tmp, sha, BUILD_ID="68", BUILD_NUMBER="68", PERMITTED_SHA_GUARD_VERSION="x")
        check("Jib: a label value that is not a hex id is refused before Jib runs", r.returncode != 0 and "not a 40-64 digit lowercase hex id" in r.stderr, r.stdout + r.stderr)
        r = jib_run(ws, tmp, sha, BUILD_ID="69", BUILD_NUMBER="69", BUILD_PLATFORM="linux/arm64,linux/amd64")
        check("Jib: a platform list or an unknown platform is refused before Jib runs", r.returncode != 0 and "refusing the Jib build" in r.stderr, r.stdout + r.stderr)
        r = jib_run(ws, tmp, sha, BUILD_ID="70", BUILD_NUMBER="70", DEV_IMAGE_TAG="dev,evil")
        check("Jib: a moving tag that would split Jib's tag list is refused before Jib runs", r.returncode != 0 and "is not a plain tag" in r.stderr, r.stdout + r.stderr)
        with open(os.path.join(ws, ".jar-sha256"), "w") as fh:
            fh.write("0" * 64 + "\n")
        r = jib_run(ws, tmp, sha, BUILD_ID="71", BUILD_NUMBER="71")
        check("Jib: a jar that is not the verified jar is refused before staging", r.returncode != 0 and "changed since it was verified" in r.stderr, r.stdout + r.stderr)
        with open(os.path.join(ws, ".jar-sha256"), "w") as fh:
            fh.write(hashlib.sha256(b"jar").hexdigest() + "\n")
        # the staged jar swapped between staging and the lock
        swap = jib_block.replace("( " + lock_block + " )", "printf x >> .jenkins-tmp/jib-options-edge-feed-gateway/app/app.jar\n( " + lock_block + " )")
        r = jib_run(ws, tmp, sha, block=swap, BUILD_ID="72", BUILD_NUMBER="72")
        check("Jib: a staged jar changed after staging is refused at the lock, no lock", r.returncode != 0 and "staged jar changed" in r.stderr
              and not os.path.exists(os.path.join(ws, ".jenkins-tmp/image-lock-72.env")), r.stdout + r.stderr)
        # the docker path is kept for builds that push nothing and for a registry outside the plain-http set
        with open(os.path.join(tmp, "bin/docker"), "w") as fh:
            fh.write("#!/usr/bin/env bash\nexit 0\n")
        for label, over in [("no push", {"PUSH_IMAGE": "false"}), ("a registry outside the plain-http set", {"INSECURE_REGISTRIES": "other:5000"})]:
            r = run(ws, tmp, sha, INSECURE_REGISTRIES=over.get("INSECURE_REGISTRIES", "localhost:5001"), **{k: v for k, v in over.items() if k != "INSECURE_REGISTRIES"}, BUILD_ID="73", BUILD_NUMBER="73",
                    block="\n".join(["set -e", "( " + prep_block + " )", "grep -x 'IMAGE_ENGINE=docker' \".jenkins-tmp/image-env-$BUILD_ID\""]))
            check(f"docker path kept: {label}", r.returncode == 0, r.stdout + r.stderr)
    finally:
        shutil.rmtree(tmp, True)

    # ---- the Jib image is the Dockerfile's image: read both files and compare what the runtime sees ----
    import re
    dockerfile = [l.strip() for l in read("Dockerfile").splitlines() if l.strip()]
    pom = read("ci/jib-image/pom.xml")
    base = re.search(r"<from><image>([^<]+)</image></from>", pom).group(1)
    entry = re.search(r"<entrypoint>(.*?)</entrypoint>", pom, re.S).group(1)
    jib_entry = re.findall(r"<arg>([^<]*)</arg>", entry)
    workdir = re.search(r"<workingDirectory>([^<]+)</workingDirectory>", pom).group(1)
    check("Jib image == Dockerfile: base image", dockerfile[0] == f"FROM {base}", f"{dockerfile[0]} vs {base}")
    check("Jib image == Dockerfile: WORKDIR", f"WORKDIR {workdir}" in dockerfile, f"{workdir}")
    d_entry = [json.loads(l[len("ENTRYPOINT "):]) for l in dockerfile if l.startswith("ENTRYPOINT ")]
    check("Jib image == Dockerfile: ENTRYPOINT", d_entry == [jib_entry], f"{d_entry} vs {jib_entry}")
    check("Jib image == Dockerfile: the jar lands at /app/app.jar (staged under app/, extra-directory root is /)",
          "COPY target/options-edge-feed-gateway-*.jar /app/app.jar" in dockerfile and "/app/app.jar" in jib_entry
          and ".jenkins-tmp/jib-options-edge-feed-gateway/app/app.jar" in jf, str(dockerfile))
    check("Jib image == Dockerfile: the Dockerfile declares no ENV, EXPOSE, USER or ARG that Jib would have to reproduce",
          not any(l.split()[0] in ("ENV", "EXPOSE", "USER", "ARG", "LABEL", "VOLUME", "HEALTHCHECK", "CMD") for l in dockerfile), str(dockerfile))
    check("Jib: the pom checks the staged jar's sha256 before Jib reads it (enforcer requireFileChecksum, validate phase, fail=true)",
          "<requireFileChecksum>" in pom and "<type>sha256</type>" in pom and "<phase>validate</phase>" in pom and "<fail>true</fail>" in pom
          and "${jib.jar.sha256}" in pom and "<requireProperty><property>jib.jar.sha256</property>" in pom, pom)
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
