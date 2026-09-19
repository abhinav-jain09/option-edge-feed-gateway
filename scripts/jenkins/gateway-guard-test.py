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
    package = "sh 'mvn -B package'"
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
    cblock = next(l for l in jf.split("\n") if "verify-permitted-tree.sh --dir .deps/options-edge-contracts" in l and l.startswith("                  "))
    ind = cblock[:len(cblock) - len(cblock.lstrip())][:-2]
    cb = ind + "timeout(time: 10, unit: 'MINUTES') {\n" + cblock + "\n" + ind + "}\n"
    assert jf.count(cb) == 2, jf.count(cb)
    r = validate_mutated("Jenkinsfile", jf.replace(cb, "", 1))
    check("M10: the image build of context '.' without the contracts-clone verify is refused", r.returncode == 1 and "consumes a path inside the nested checkout '.deps/options-edge-contracts'" in r.stdout, r.stdout)
    pblock_line = next(l for l in jf.split("\n") if "verify-permitted-tree.sh --dir . " in l and l.startswith("                  "))
    pb = ind + "timeout(time: 10, unit: 'MINUTES') {\n" + pblock_line + "\n" + ind + "}\n"
    r = validate_mutated("Jenkinsfile", jf.replace(pb, "", 1))
    check("M10: the image build without the primary verify is refused", r.returncode == 1 and "the primary checkout '.'" in r.stdout, r.stdout)
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
                          (".jar-sha256", "e\n"), (".m2/repository/x.jar", "j\n"), ("target/x.jar", "j\n"), (".jenkins-tmp/l", "l\n")]:
            p = os.path.join(co, rel)
            os.makedirs(os.path.dirname(p), exist_ok=True)
            open(p, "w").write(body)
        st = subprocess.run(["git", "-C", co, "status", "--porcelain"], capture_output=True, text=True).stdout.strip()
        check("N4: the build's root byproducts are gitignored, so the footprint gate sees a clean tree", st == "", f"git status not empty:\n{st}")
        # the primary verify in front of `mvn package` and the image build must ACCEPT exactly what this build writes (its
        # --allow-ignored list parsed from the real Jenkinsfile), with the contracts clone and Python bytecode present too
        for rel, body in [(".deps/options-edge-contracts/pom.xml", "<project/>\n"), (".jenkins-tmp/java-home", "/x\n"), ("scripts/__pycache__/m.pyc", "c")]:
            p = os.path.join(co, rel)
            os.makedirs(os.path.dirname(p), exist_ok=True)
            open(p, "w").write(body)
        vline = next(l for l in jf.split("\n") if "verify-permitted-tree.sh --dir . " in l)
        allow = vline.split("verify-permitted-tree.sh --dir . ", 1)[1].rstrip("'").split()
        head = subprocess.run(["git", "-C", co, "rev-parse", "HEAD"], capture_output=True, text=True).stdout.strip()
        r = subprocess.run(["bash", os.path.join(HERE, "verify-permitted-tree.sh"), "--dir", co] + allow, capture_output=True, text=True,
                           env={**os.environ, "PERMITTED_SHA": head})
        check("the primary verify accepts the real post-build workspace (declared byproducts only)", r.returncode == 0, r.stdout + r.stderr)
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
