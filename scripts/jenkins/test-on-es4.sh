#!/usr/bin/env bash
# The large gateway correctness/capacity suites run on es4, outside its live cash window.
# Tests consume a verified Git snapshot and the exact contracts jar installed by THIS Jenkins build.
# Artifact/image packaging and its existing guards remain in the original Jenkins workspace.
set -euo pipefail

check_window() {
  local weekday hhmm zone
  read -r weekday hhmm zone < <(TZ=America/New_York date '+%u %H%M %Z')
  [[ $zone = EST || $zone = EDT ]] || { echo 'New York timezone unavailable' >&2; return 1; }
  # A test run is bounded to 50 minutes: do not start inside the 60-minute pre-open margin.
  if [ "$weekday" -le 5 ] && [ "$hhmm" -ge 0830 ] && [ "$hhmm" -lt 1600 ]; then
    echo 'Gateway es4 tests refused: cash-window exclusion is 08:30-16:00 America/New_York.' >&2
    return 1
  fi
}
if [ "${1:-}" = --check-window ]; then check_window; exit; fi
[ $# -eq 0 ] || { echo 'No positional arguments accepted' >&2; exit 64; }
[ -n "${JENKINS_URL:-}" ] || { echo 'This transport runs only inside Jenkins' >&2; exit 64; }
[[ ${PERMITTED_SHA:-} =~ ^[0-9a-f]{40}$ ]] || exit 64
[[ ${CONTRACTS_PERMITTED_SHA:-} =~ ^[0-9a-f]{40}$ ]] || exit 64
[[ ${BUILD_NUMBER:-} =~ ^[0-9]+$ ]] || exit 64
check_window
bash scripts/jenkins/permitted-sha-guard.sh
PERMITTED_SHA="${PERMITTED_SHA:?}" bash scripts/jenkins/verify-permitted-tree.sh --dir . \
  --allow-ignored target --allow-ignored dev/raw-feed-replicator/target --allow-ignored .m2 \
  --allow-ignored .deps --allow-ignored .jenkins-tmp --allow-ignored .contracts-sha \
  --allow-ignored .contracts-version --allow-ignored .contracts-jar-sha256 --allow-ignored .jar-sha256 \
  --allow-ignored scripts/__pycache__ --allow-ignored scripts/jenkins/__pycache__
[ "$(cat .contracts-sha)" = "$CONTRACTS_PERMITTED_SHA" ] || { echo 'Contracts provenance mismatch' >&2; exit 1; }
# The pom's pin is deliberately NOT consulted. This pipeline installs contracts from main's tip and
# builds against the version it installed, which it passes as -Doptions-edge-contracts.version; a pin
# in the pom is only the local-dev default and is stale by design the moment contracts bumps. Requiring
# the two to agree is what forced a pin-chasing commit on every contracts release. Provenance does not
# rest on the pom: .contracts-sha is checked against CONTRACTS_PERMITTED_SHA above, and the jar at the
# installed version has its sha256 verified below and again on es4 before any test runs.
cv=$(cat .contracts-version)
[[ $cv =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || exit 64
contracts_jar="$PWD/.m2/repository/com/optionsedge/options-edge-contracts/$cv/options-edge-contracts-$cv.jar"
[ "$(bash scripts/jenkins/permitted-sha-guard-version.sh "$contracts_jar")" = "$(cat .contracts-jar-sha256)" ] \
  || { echo 'Installed contracts jar changed' >&2; exit 1; }

host=abhinav@192.168.100.4
remote_root=/home/abhinav/ci/gateway-tests
java_home=/usr/lib/jvm/java-21-openjdk
ssh_args=(-o BatchMode=yes -o ConnectTimeout=10 -o StrictHostKeyChecking=yes -o ServerAliveInterval=10 -o ServerAliveCountMax=3)
local_dir="$PWD/.jenkins-tmp/es4-tests-$BUILD_NUMBER"
[ ! -e "$local_dir" ] || { echo "Test transport already attempted for this build" >&2; exit 1; }
mkdir -p "$local_dir"
[ -z "$(find "$PWD/.m2/repository" -type l -print -quit)" ] || { echo "Maven cache contains a symlink" >&2; exit 1; }
[ "$(du -sk "$PWD/.m2/repository" | cut -f1)" -le 4194304 ] || { echo "Test cache exceeds 4 GiB" >&2; exit 1; }
cleanup_local() {
  rm -f "$local_dir/source.bundle" "$local_dir/maven-cache.tar.gz" "$local_dir/inputs.sha256" \
    "$local_dir/.contracts-sha" "$local_dir/.contracts-version" "$local_dir/.contracts-jar-sha256"
}
trap cleanup_local EXIT
git bundle create "$local_dir/source.bundle" HEAD
COPYFILE_DISABLE=1 tar -C "$PWD/.m2" -czf "$local_dir/maven-cache.tar.gz" repository
cp .contracts-sha .contracts-version .contracts-jar-sha256 "$local_dir/"
(
  cd "$local_dir"
  shasum -a 256 source.bundle maven-cache.tar.gz .contracts-sha .contracts-version .contracts-jar-sha256 > inputs.sha256
)
# This root contains only this transport's private test workspaces. Refuse growth after failed cleanup.
remote_dir=$(ssh "${ssh_args[@]}" "$host" "bash -s -- $remote_root $BUILD_NUMBER" <<'REMOTE_INIT'
set -euo pipefail
root=$1; build=$2
[ "$root" = /home/abhinav/ci/gateway-tests ] || exit 64
umask 077
mkdir -p "$root"
[ ! -L "$root" ] || exit 1
for tool in mvn git python3 systemd-run systemctl docker sha256sum tar taskset; do command -v "$tool" >/dev/null; done
[ -x /usr/lib/jvm/java-21-openjdk/bin/java ] || exit 1
[ -r /usr/share/zoneinfo/America/New_York ] || exit 1
manager_state=$(systemctl --user is-system-running || true)
[[ $manager_state = running || $manager_state = degraded ]] || exit 1
docker info >/dev/null
# Verify the effective kernel limits and Docker access INSIDE the same clean unit context.
cpu_list=$(python3 -c 'import os; cpus=sorted(os.sched_getaffinity(0)); assert len(cpus)>=2; print(str(cpus[0])+","+str(cpus[1]))')
probe="oe-gateway-probe-$build-$RANDOM-$RANDOM"
systemd-run --user --quiet --wait --pipe --collect --unit="$probe" \
  --property=MemoryMax=4G --property=MemorySwapMax=0 --property=TasksMax=512 \
  --property=RuntimeMaxSec=15 --property=TimeoutStopSec=5 \
  --property=KillMode=control-group taskset --cpu-list "$cpu_list" env -i \
  PATH=/usr/lib/jvm/java-21-openjdk/bin:/usr/bin:/bin CI_CPU_LIST="$cpu_list" \
  bash -c '
    set -euo pipefail
    group=$(sed -n "s/^0:://p" /proc/self/cgroup)
    base="/sys/fs/cgroup$group"
    [ "$(cat "$base/memory.max")" = 4294967296 ]
    [ "$(cat "$base/memory.swap.max")" = 0 ]
    [ "$(cat "$base/pids.max")" = 512 ]
    python3 -c "import os; assert os.sched_getaffinity(0)==set(map(int,os.environ[\"CI_CPU_LIST\"].split(\",\")))"
    docker info >/dev/null
    echo "Effective CI unit limits and clean-environment Docker access verified"
  ' >&2
[ "$(df -Pk "$root" | awk 'NR==2 {print $4}')" -ge 41943040 ] || { echo '40 GiB free disk required' >&2; exit 1; }
[ "$(find "$root" -mindepth 1 -maxdepth 1 -type d | wc -l)" -lt 4 ] || { echo 'Too many retained CI workspaces' >&2; exit 1; }
[ "$(du -sm "$root" | cut -f1)" -lt 20480 ] || { echo 'CI retained-state budget exceeded (20 GiB)' >&2; exit 1; }
dir=$(mktemp -d "$root/run.$build.XXXXXXXX")
printf '%s\n' "$cpu_list" > "$dir/cpus.txt"
printf '%s\n' "$dir"
REMOTE_INIT
)
[[ $remote_dir =~ ^/home/abhinav/ci/gateway-tests/run\.[0-9]+\.[A-Za-z0-9]+$ ]] || exit 64
finish() {
  local result=$?
  trap - EXIT INT TERM HUP
  # Stop only this run's transient service, after proving its ExecStart ownership.
  ssh "${ssh_args[@]}" "$host" "bash -s -- $remote_dir" <<'REMOTE_FINISH' || true
set -euo pipefail
dir=$1
[[ $dir =~ ^/home/abhinav/ci/gateway-tests/run\.[0-9]+\.[A-Za-z0-9]+$ ]] || exit 64
# The unit name is derived exclusively from this validated private run directory.
unit="oe-gateway-$(basename "$dir" | tr '.' '-')"
# A unit belonging to a different command must never be stopped.
exec_start=$(systemctl --user show "$unit" --property=ExecStart --value 2>/dev/null || true)
if [[ $exec_start = *"$dir/run-tests.sh"* ]]; then
  systemctl --user stop "$unit"
fi
REMOTE_FINISH
  # Transfer failures cannot pass the subsequent mandatory receipt/evidence verification.
  scp "${ssh_args[@]}" -r "$host:$remote_dir/surefire-reports" "$local_dir/" >/dev/null 2>&1 || true
  # Missing success-only files must not prevent transfer of the failure log.
  for evidence in test.log receipt.json complete.txt; do
    scp "${ssh_args[@]}" "$host:$remote_dir/$evidence" "$local_dir/" >/dev/null 2>&1 || true
  done
  scp "${ssh_args[@]}" "$host:$remote_dir/gateway/target/footprint-reverify.json" "$local_dir/" >/dev/null 2>&1 || true
  ssh "${ssh_args[@]}" "$host" "bash -s -- $remote_dir" <<'REMOTE_CLEAN' || true
set -euo pipefail
dir=$1
[[ $dir =~ ^/home/abhinav/ci/gateway-tests/run\.[0-9]+\.[A-Za-z0-9]+$ ]] || exit 64
[ -d "$dir" ] && [ ! -L "$dir" ] || exit 1
# -P never follows a symlink, including a concurrently replaced root; no recursive rm.
timeout 120s find -P "$dir" -xdev -depth -mindepth 1 -delete
rmdir "$dir"
REMOTE_CLEAN
  # Keep only bounded report/log evidence locally; source/cache snapshots are not retained per build.
  cleanup_local
  exit "$result"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM HUP
scp "${ssh_args[@]}" "$local_dir/source.bundle" "$local_dir/maven-cache.tar.gz" \
  "$local_dir/.contracts-sha" "$local_dir/.contracts-version" "$local_dir/.contracts-jar-sha256" \
  "$local_dir/inputs.sha256" "$host:$remote_dir/"
ssh "${ssh_args[@]}" "$host" "bash -s -- $remote_dir $PERMITTED_SHA $CONTRACTS_PERMITTED_SHA $java_home $BUILD_NUMBER" <<'REMOTE_TEST'
set -euo pipefail
[ $# -eq 5 ] || exit 64
dir=$1; source_sha=$2; contracts_sha=$3; java_home=$4; build=$5
[[ $build =~ ^[0-9]+$ ]] || exit 64
[[ $dir =~ ^/home/abhinav/ci/gateway-tests/run\.[0-9]+\.[A-Za-z0-9]+$ ]] || exit 64
[[ $source_sha =~ ^[0-9a-f]{40}$ ]] && [[ $contracts_sha =~ ^[0-9a-f]{40}$ ]] || exit 64
[ "$java_home" = /usr/lib/jvm/java-21-openjdk ] || exit 64
read -r weekday hhmm zone < <(TZ=America/New_York date '+%u %H%M %Z')
[[ $zone = EST || $zone = EDT ]] || exit 1
if [ "$weekday" -le 5 ] && [ "$hhmm" -ge 0830 ] && [ "$hhmm" -lt 1600 ]; then exit 1; fi
cd "$dir"
sha256sum -c inputs.sha256
[ "$(cat .contracts-sha)" = "$contracts_sha" ] || exit 1
mkdir gateway cache tmp
# The main Jenkins checkout already passed its LIVE main-tip guard. This test-only copy
# verifies the exact commit and tree from that checkout, not a different GitHub credential.
git -C gateway init -q
git -C gateway fetch -q ../source.bundle HEAD
git -C gateway checkout -q -b main FETCH_HEAD
[ "$(git -C gateway rev-parse HEAD)" = "$source_sha" ] || exit 1
PERMITTED_SHA="$source_sha" bash gateway/scripts/jenkins/verify-permitted-tree.sh --dir gateway
# The checksummed cache was produced in this build's private repository, not a shared ~/.m2.
tar -xzf maven-cache.tar.gz -C cache
cv=$(cat .contracts-version)
[[ $cv =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || exit 64
jar="cache/repository/com/optionsedge/options-edge-contracts/$cv/options-edge-contracts-$cv.jar"
[ "$(sha256sum "$jar" | cut -d' ' -f1)" = "$(cat .contracts-jar-sha256)" ] || exit 1
# EVERY maven in this run must resolve the contracts jar this build INSTALLED, not the pom's pin.
# The suite names the version on its own command line, but the footprint campaign's maven comes from a
# COMMITTED spec whose argv is part of the campaign record and so cannot carry a per-build version. With
# nothing else to tell it, the campaign's BASELINE resolved the stale pin, found no such jar in this
# build's private repository, went to the network and reported "BASELINE IS RED — refusing to run a
# campaign against a failing suite" before a single mutation ran. Every gateway build failed that way
# from the moment contracts main moved past the pin, and the console log showed none of it, because
# run-tests.sh writes to test.log.
#
# maven.config goes in the RUN ROOT, not in the gateway checkout: maven walks UP from the project
# directory to find .mvn, so the checkout stays exactly the tree verify-permitted-tree just passed.
# MAVEN_OPTS cannot carry it (a JVM system property loses to a pom property) and MAVEN_ARGS needs
# maven 3.9, where es4 has 3.6.3 — both measured, neither assumed.
mkdir -p .mvn
printf -- '-Doptions-edge-contracts.version=%s\n' "$cv" > .mvn/maven.config
"$java_home/bin/java" -version
cat > run-tests.sh <<'RUN'
#!/usr/bin/env bash
set -euo pipefail
cd "$CI_GATEWAY_DIR"
java -version
mvn -version
taskset -pc $$
result=0
# THE INSTALLED CONTRACTS VERSION, not this pom's pin. The workspace was built against the contracts
# jar that Install Contracts compiled from main's tip; the pom's pin is only the local-dev default and
# goes stale the moment contracts bumps. CI_CONTRACTS_VERSION is set from .contracts-version by the
# supervisor below, and the sha256 of that exact jar is already verified before transfer.
: "${CI_CONTRACTS_VERSION:?the supervisor did not pass the installed contracts version}"
mvn -B test -Doptions-edge-contracts.version="$CI_CONTRACTS_VERSION" || result=$?
# Keep bounded failing reports too, before exiting or running mutations that overwrite XML.
python3 scripts/jenkins/es4-test-receipt.py collect target/surefire-reports ..
[ "$result" -eq 0 ] || exit "$result"
scripts/footprint-reverify.sh
scripts/footprint-reqstate.sh --check
printf '%s\n' "$CI_SOURCE_SHA" > ../complete.txt
RUN
# Build #800 passed all 1,617 tests but hit the 40-minute cap near the end of
# its 50-case mutation campaign. Allow 50 minutes, still below the 60-minute
# pre-open exclusion margin; retain the CPU, memory, disk and log watchdogs.
# Successful execution is required before any image is packaged or deployed.
unit="oe-gateway-$(basename "$dir" | tr '.' '-')"
stop_unit() { systemctl --user stop "$unit" >/dev/null 2>&1 || true; }
trap stop_unit EXIT
trap 'exit 143' HUP INT TERM
read -r weekday hhmm zone < <(TZ=America/New_York date '+%u %H%M %Z')
[[ $zone = EST || $zone = EDT ]] || exit 1
if [ "$weekday" -le 5 ] && [ "$hhmm" -ge 0830 ] && [ "$hhmm" -lt 1600 ]; then exit 1; fi
# User-systemd owns every descendant, including TERM-resistant/orphaned processes.
# The Kafka test container separately has 2 GiB / one CPU limits; planned total is 8 GiB.
cpu_list=$(cat "$dir/cpus.txt")
[[ $cpu_list =~ ^[0-9]+,[0-9]+$ ]] || exit 64
python3 - "$dir" "$unit" "$java_home" "$source_sha" "$cpu_list" "$cv" <<'SUPERVISE'
import os, pathlib, selectors, subprocess, sys, time
root, unit, java, source, cpus, contracts_version = sys.argv[1:]
command = ["systemd-run", "--user", "--quiet", "--wait", "--pipe", "--collect", "--unit=" + unit,
    "--property=MemoryMax=4G", "--property=MemorySwapMax=0", "--property=TasksMax=512",
    "--property=RuntimeMaxSec=3000", "--property=TimeoutStopSec=10",
    "--property=KillMode=control-group", "taskset", "--cpu-list", cpus, "env", "-i", "PATH=" + java + "/bin:/usr/bin:/bin",
    "JAVA_HOME=" + java, "JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=" + root + "/tmp",
    "MAVEN_SKIP_RC=true", "MAVEN_OPTS=-Xmx768m -DreuseForks=false -Dmaven.repo.local=" + root + "/cache/repository -Djava.io.tmpdir=" + root + "/tmp",
    "CI_GATEWAY_DIR=" + root + "/gateway", "CI_SOURCE_SHA=" + source,
    # env -i wipes the environment, so the installed contracts version has to be named here or the
    # remote mvn silently falls back to the pom's (possibly stale) pin.
    "CI_CONTRACTS_VERSION=" + contracts_version,
    "JENKINS_URL=es4-test-transport", "CI=true", "bash", root + "/run-tests.sh"]
child = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
selector = selectors.DefaultSelector()
selector.register(child.stdout, selectors.EVENT_READ)
size = 0
next_disk_check = 0.0
disk_timeouts = 0
try:
    with open(root + "/test.log", "wb") as log:
        while selector.get_map():
            if time.monotonic() >= next_disk_check:
                try:
                    usage = subprocess.run(["du", "-sk", str(pathlib.Path(root).parent)],
                        timeout=10, capture_output=True, env={**os.environ, "LC_ALL": "C"})
                except subprocess.TimeoutExpired:
                    disk_timeouts += 1
                    if disk_timeouts >= 2:
                        raise RuntimeError("CI disk watchdog timed out twice")
                    next_disk_check = time.monotonic() + 1
                else:
                    disk_timeouts = 0
                    errors = usage.stderr.decode(errors="replace").splitlines()
                    if usage.returncode not in (0, 1) or not usage.stdout.split() or \
                            (usage.returncode == 1 and any("No such file or directory" not in line for line in errors)):
                        raise RuntimeError("CI disk watchdog could not measure private state")
                    if int(usage.stdout.split()[0]) > 20 * 1024 * 1024:
                        raise RuntimeError("CI retained-state threshold exceeds 20 GiB")
                    next_disk_check = time.monotonic() + 10
            for key, _ in selector.select(timeout=5):
                block = os.read(key.fileobj.fileno(), 65536)
                if not block:
                    selector.unregister(key.fileobj)
                    continue
                size += len(block)
                if size > 128 << 20:
                    raise RuntimeError("test log exceeds 128 MiB")
                log.write(block)
    if child.wait(timeout=15) != 0:
        raise RuntimeError("remote full suite or campaign failed")
    if pathlib.Path(root, "complete.txt").read_text() != source + "\n":
        raise RuntimeError("test sequence did not reach its final completion marker")
finally:
    selector.close()
    # Stop is idempotent for a collected unit. KillMode kills ALL members, not only a leader PID.
    subprocess.run(["systemctl", "--user", "stop", unit], timeout=20, check=False,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if child.poll() is None:
        child.terminate()
        child.wait(timeout=15)
SUPERVISE
tail -n 40 test.log
PERMITTED_SHA="$source_sha" bash gateway/scripts/jenkins/verify-permitted-tree.sh --dir gateway --allow-ignored target --allow-ignored scripts/__pycache__
python3 gateway/scripts/jenkins/es4-test-receipt.py write "$dir" "$source_sha" "$contracts_sha" "$(cat .contracts-jar-sha256)" "$build"
REMOTE_TEST
# finish() fetches the receipt before returning this successful exit to Jenkins.
exit 0
