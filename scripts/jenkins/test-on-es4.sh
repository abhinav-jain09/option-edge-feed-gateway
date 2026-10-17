#!/usr/bin/env bash
# The large gateway correctness/capacity suites run on es4, outside its live cash window.
# Tests consume a verified Git snapshot and the exact contracts jar installed by THIS Jenkins build.
# Artifact/image packaging and its existing guards remain in the original Jenkins workspace.
set -euo pipefail

check_window() {
  local weekday hhmm
  read -r weekday hhmm < <(TZ=America/New_York date '+%u %H%M')
  # A test run is bounded to 40 minutes: do not start inside the 45-minute pre-open margin.
  if [ "$weekday" -le 5 ] && [ "$hhmm" -ge 0845 ] && [ "$hhmm" -lt 1600 ]; then
    echo 'Gateway es4 tests refused: cash-window exclusion is 08:45-16:00 America/New_York.' >&2
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
cv=$(cat .contracts-version)
[[ $cv =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || exit 64
python3 - "$cv" <<'PIN'
import sys, xml.etree.ElementTree as ET
root=ET.parse('pom.xml').getroot()
actual=root.find('{http://maven.apache.org/POM/4.0.0}properties/{http://maven.apache.org/POM/4.0.0}options-edge-contracts.version')
if actual is None or (actual.text or "").strip()!=sys.argv[1]:
    raise SystemExit('Gateway dependency pin differs from the installed contracts artifact')
PIN
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
git bundle create "$local_dir/source.bundle" HEAD
tar -C "$PWD/.m2" -czf "$local_dir/maven-cache.tar.gz" repository
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
[ "$(find "$root" -mindepth 1 -maxdepth 1 -type d | wc -l)" -lt 4 ] || { echo 'Too many retained CI workspaces' >&2; exit 1; }
[ "$(du -sm "$root" | cut -f1)" -lt 20480 ] || { echo 'CI retained-state budget exceeded (20 GiB)' >&2; exit 1; }
mktemp -d "$root/run.$build.XXXXXXXX"
REMOTE_INIT
)
[[ $remote_dir =~ ^/home/abhinav/ci/gateway-tests/run\.[0-9]+\.[A-Za-z0-9]+$ ]] || exit 64
finish() {
  local result=$?
  trap - EXIT INT TERM HUP
  # Stop only this run's isolated process group, if it is still the process we recorded.
  ssh "${ssh_args[@]}" "$host" "bash -s -- $remote_dir" <<'REMOTE_FINISH' || true
set -euo pipefail
dir=$1
[[ $dir =~ ^/home/abhinav/ci/gateway-tests/run\.[0-9]+\.[A-Za-z0-9]+$ ]] || exit 64
if [ -f "$dir/test.pid" ]; then
  pid=$(cat "$dir/test.pid")
  if [[ $pid =~ ^[0-9]+$ ]] && [ -r "/proc/$pid/cmdline" ] \
      && tr '\0' ' ' < "/proc/$pid/cmdline" | grep -Fq -- "$dir" \
      && [ "$(ps -o pgid= -p "$pid" | tr -d ' ')" = "$pid" ]; then
    kill -TERM -- "-$pid" || true
    for i in 1 2 3 4 5; do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
    if kill -0 "$pid" 2>/dev/null && [ -r "/proc/$pid/cmdline" ] \
        && tr '\0' ' ' < "/proc/$pid/cmdline" | grep -Fq -- "$dir"; then kill -KILL -- "-$pid" || true; fi
  fi
fi
REMOTE_FINISH
  # Reports are supplementary; a missing report never turns a failed test command into success.
  scp "${ssh_args[@]}" -r "$host:$remote_dir/gateway/target/surefire-reports" "$local_dir/" >/dev/null 2>&1 || true
  scp "${ssh_args[@]}" "$host:$remote_dir/test.log" "$host:$remote_dir/receipt.txt" "$local_dir/" >/dev/null 2>&1 || true
  ssh "${ssh_args[@]}" "$host" "bash -s -- $remote_dir" <<'REMOTE_CLEAN' || true
set -euo pipefail
dir=$1
[[ $dir =~ ^/home/abhinav/ci/gateway-tests/run\.[0-9]+\.[A-Za-z0-9]+$ ]] || exit 64
[ -d "$dir" ] && [ ! -L "$dir" ] || exit 1
# -P never follows a symlink, including a concurrently replaced root; no recursive rm.
find -P "$dir" -xdev -depth -mindepth 1 -delete
rmdir "$dir"
REMOTE_CLEAN
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
read -r weekday hhmm < <(TZ=America/New_York date '+%u %H%M')
if [ "$weekday" -le 5 ] && [ "$hhmm" -ge 0845 ] && [ "$hhmm" -lt 1600 ]; then exit 1; fi
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
"$java_home/bin/java" -version
cat > run-tests.sh <<'RUN'
#!/usr/bin/env bash
set -euo pipefail
cd "$CI_GATEWAY_DIR"
mvn -B test
scripts/footprint-reverify.sh
scripts/footprint-reqstate.sh --check
RUN
# Planned host budget: 8 GiB including the 1536 MiB Surefire heap, 1 GiB native headroom,
# Maven (768 MiB heap plus 512 MiB headroom) and isolated Testcontainers Kafka resources.
# This is an isolated CI process, with the source/Maven cache under this run's directory.
read -r weekday hhmm < <(TZ=America/New_York date '+%u %H%M')
if [ "$weekday" -le 5 ] && [ "$hhmm" -ge 0845 ] && [ "$hhmm" -lt 1600 ]; then exit 1; fi
setsid timeout 2400s env -i PATH="$java_home/bin:/usr/bin:/bin" JAVA_HOME="$java_home" \
  JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$dir/tmp" \
  MAVEN_SKIP_RC=true MAVEN_OPTS="-Xmx768m -Dmaven.repo.local=$dir/cache/repository -Djava.io.tmpdir=$dir/tmp" \
  CI_GATEWAY_DIR="$dir/gateway" JENKINS_URL=es4-test-transport CI=true \
  bash "$dir/run-tests.sh" > test.log 2>&1 &
pid=$!; printf '%s\n' "$pid" > test.pid
result=0; wait "$pid" || result=$?
tail -n 40 test.log
[ "$result" -eq 0 ] || exit "$result"
printf 'source=%s\ncontracts_source=%s\ncontracts_jar_sha256=%s\nbuild_number=%s\nstatus=PASS\n' \
  "$source_sha" "$contracts_sha" "$(cat .contracts-jar-sha256)" "$build" > receipt.txt
REMOTE_TEST
if [ -f "$local_dir/receipt.txt" ]; then echo 'Stale local test receipt exists' >&2; exit 1; fi
# finish() fetches the receipt before returning this successful exit to Jenkins.
exit 0
