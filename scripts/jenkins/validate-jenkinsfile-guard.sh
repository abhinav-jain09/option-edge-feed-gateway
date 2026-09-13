#!/usr/bin/env bash
# Both Jenkinsfiles carry the permitted-commit guard (Deployment Permission Rule, options-edge rule.md)
# in the right POSITION and the right FORM, and the guard script refuses every unpermitted case.
#
#   Jenkinsfile         image build+push: the guard is the FIRST nested stage of Build (before Install
#                       Contracts, Test, Footprint reverification, Package, Image); the dev rollout's
#                       inputs are preflighted before any build step; the contracts clone is bound by
#                       its own guard (--dir, --ref) before mvn install; the dev Deploy+verify trigger
#                       is preceded by the fail-closed downstream compatibility check and forwards
#                       DEPLOY_PERMITTED_SHA as PERMITTED_SHA.
#   Jenkinsfile.deploy  host java process: guard after the main-only checkout, before Install Contracts;
#                       the contracts clone bound before mvn install.
#
# Textual, deliberately strict: the guard stage must be the CANONICAL form (sh(returnStatus: true, …);
# if (rc != 0) { error(…) }; env.PERMITTED_SHA_GUARD = 'PASSED'; nothing else) — `rc == 0`, a
# catchError, a missing error() or an extra statement fail here. Stage order is the order of
# stage('...') lines; a stage built dynamically would not be seen. No pipeline is executed.
set -euo pipefail
cd "$(dirname "$0")/../.."
fail=0
say() { echo "FAIL: $*"; fail=1; }

bash scripts/jenkins/permitted-sha-guard-test.sh | tail -1 | grep -q 'ALL PASS' || say "guard self-test did not report ALL PASS"

order() { grep -oE "^[[:space:]]*stage\('[^']*'" "$1" | sed -E "s/^[[:space:]]*stage\('//; s/'$//"; }
idx() { order "$1" | grep -nFx "$2" | head -1 | cut -d: -f1; }
# the guard stage's text, comments and blank lines dropped, whitespace collapsed
guard_canon() {
  awk -v s="stage('Permitted commit guard')" 'index($0,s){p=1} p{print} p && /^    }$/{exit}' "$1" \
    | grep -vE '^[[:space:]]*//' | grep -v '^[[:space:]]*$' | tr -s '[:space:]' ' ' | sed 's/^ //; s/ $//'
}
CANON="stage('Permitted commit guard') { steps { script { def rc = sh(returnStatus: true, script: 'bash scripts/jenkins/permitted-sha-guard.sh') if (rc != 0) { error(\"MSG\") } env.PERMITTED_SHA_GUARD = 'PASSED' } } }"
# lines that are effects (not comments)
effects() { { grep -nE "\bmvn\b|docker (build|buildx|push)|\bbuild job:|\brsync\b|\bscp\b|\bssh\b|\bkill\b|\bnohup\b" "$1" || true; } | { grep -vE '^[0-9]+:[[:space:]]*(//|#)' || true; }; }

for f in Jenkinsfile Jenkinsfile.deploy; do
  grep -q "string(name: 'PERMITTED_SHA', defaultValue: '', trim: true" "$f" || say "$f: PERMITTED_SHA parameter missing or not declared with defaultValue: '' and trim: true"
  grep -q "string(name: 'CONTRACTS_PERMITTED_SHA', defaultValue: '', trim: true" "$f" || say "$f: CONTRACTS_PERMITTED_SHA parameter missing"
  grep -q "disableRestartFromStage()" "$f" || say "$f: options {} lacks disableRestartFromStage()"
  [ -n "$(idx "$f" 'Permitted commit guard')" ] || say "$f: no 'Permitted commit guard' stage"
  got="$(guard_canon "$f" | sed -E 's/error\("[^"]*"\)/error("MSG")/')"
  [ "$got" = "$CANON" ] || say "$f: the guard stage is not the canonical form: $got"
  # the contracts clone is bound before it is installed
  cl=$(grep -n 'git clone' "$f" | head -1 | cut -d: -f1); cg=$(grep -n 'permitted-sha-guard.sh.*--dir .deps/options-edge-contracts' "$f" | head -1 | cut -d: -f1); mi=$(grep -n 'options-edge-contracts/pom.xml install' "$f" | head -1 | cut -d: -f1)
  { [ -n "$cl" ] && [ -n "$cg" ] && [ -n "$mi" ] && [ "$cl" -lt "$cg" ] && [ "$cg" -lt "$mi" ]; } || say "$f: the contracts clone must be guarded (--dir .deps/options-edge-contracts) between the clone (line ${cl:-?}) and mvn install (line ${mi:-?}); guard at line ${cg:-?}"
  grep -q 'permitted-sha-guard.sh.*--dir .deps/options-edge-contracts --ref' "$f" || say "$f: the contracts guard must judge the selected ref (--ref)"
  # no effect precedes the guard line, and no checkout/pull of THIS repository follows it
  g=$(grep -n "stage('Permitted commit guard')" "$f" | head -1 | cut -d: -f1)
  st=$(grep -n '^  stages {' "$f" | head -1 | cut -d: -f1)   # constants and parameter descriptions above stages{} are text, not steps
  early="$(effects "$f" | awk -F: -v g="$g" -v st="$st" '$1 > st && $1 < g')"
  [ -z "$early" ] || say "$f: an effect precedes the guard: $early"
  awk -v g="$g" 'NR > g' "$f" | grep -vE '^[[:space:]]*(//|#)' | grep -qE '\bcheckout\(|\bcheckout scm\b|\bgit pull\b|git checkout [^m]' && say "$f: a checkout/pull of this repository follows the guard"
done

# Jenkinsfile: Build's nested order is guard < Deploy preflight < Install Contracts < ... < Image < Deploy + verify (dev)
g=$(idx Jenkinsfile 'Permitted commit guard'); pf=$(idx Jenkinsfile 'Deploy preflight (dev rollout inputs)'); ic=$(idx Jenkinsfile 'Install Contracts'); i=$(idx Jenkinsfile 'Image'); d=$(idx Jenkinsfile 'Deploy + verify (dev)')
{ [ "$g" -lt "$pf" ] && [ "$pf" -lt "$ic" ] && [ "$ic" -lt "$i" ] && [ "$i" -lt "$d" ]; } || say "Jenkinsfile: stage order must be Permitted commit guard < Deploy preflight < Install Contracts < Image < Deploy + verify (dev) (got G=$g P=$pf IC=$ic I=$i D=$d)"
prev=$(order Jenkinsfile | sed -n "$((g-1))p"); [ "$prev" = "Build" ] || say "Jenkinsfile: the guard must be the first nested stage of Build (preceded by '$prev')"
# The downstream rollout is bound AND fail-closed: DEPLOY_PERMITTED_SHA validated, service-deploy's live
# definition checked, both in the preflight and again right before the trigger; the SHA forwarded.
grep -q "string(name: 'DEPLOY_PERMITTED_SHA', defaultValue: '', trim: true" Jenkinsfile || say "Jenkinsfile: DEPLOY_PERMITTED_SHA parameter missing"
[ "$(grep -c "dsha.matches('^\[0-9a-f\]{40}\$')" Jenkinsfile)" -ge 2 ] || say "Jenkinsfile: DEPLOY_PERMITTED_SHA must be validated in the preflight and before the trigger"
[ "$(grep -c "require-guarded-downstream.sh service-deploy" Jenkinsfile)" -ge 2 ] || say "Jenkinsfile: require-guarded-downstream.sh service-deploy must run in the preflight and before the trigger"
bj=$(grep -n "build job: 'service-deploy'" Jenkinsfile | head -1 | cut -d: -f1); cc=$(grep -n "require-guarded-downstream.sh service-deploy" Jenkinsfile | tail -1 | cut -d: -f1)
{ [ -n "$bj" ] && [ -n "$cc" ] && [ "$cc" -lt "$bj" ]; } || say "Jenkinsfile: the compatibility check must precede build job: 'service-deploy'"
grep -q "string(name: 'PERMITTED_SHA', value: dsha)" Jenkinsfile || say "Jenkinsfile: service-deploy trigger does not forward PERMITTED_SHA"
grep -q "UNBOUND" Jenkinsfile && say "Jenkinsfile: an UNBOUND annotation is not a gate"

# Jenkinsfile.deploy: Checkout (main only) < guard < Install Contracts.
c=$(idx Jenkinsfile.deploy 'Checkout (main only)'); g=$(idx Jenkinsfile.deploy 'Permitted commit guard'); n=$(idx Jenkinsfile.deploy 'Install Contracts')
{ [ "$c" -lt "$g" ] && [ "$g" -lt "$n" ]; } || say "Jenkinsfile.deploy: stage order must be Checkout (main only) < Permitted commit guard < Install Contracts (got C=$c G=$g I=$n)"

[ "$fail" -eq 0 ] && echo "OK: both Jenkinsfiles carry the canonical permitted-commit guard before their first effect, bind the contracts clone, and gate the downstream rollout"
exit "$fail"
