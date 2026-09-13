#!/usr/bin/env bash
# Both Jenkinsfiles carry the permitted-commit guard (Deployment Permission Rule, options-edge rule.md)
# in the right POSITION, and the guard script refuses every unpermitted case.
#
#   Jenkinsfile         image build+push: guard after Package, before Image (the workspace the jar and
#                       the image are built from; no checkout between guard and push); the dev
#                       Deploy+verify trigger forwards DEPLOY_PERMITTED_SHA to service-deploy.
#   Jenkinsfile.deploy  host java process: guard after the main-only checkout, before Install Contracts.
#
# Textual: stage order is the order of stage('...') lines; a stage built dynamically would not be seen.
set -euo pipefail
cd "$(dirname "$0")/../.."
fail=0
say() { echo "FAIL: $*"; fail=1; }

bash scripts/jenkins/permitted-sha-guard-test.sh | tail -1 | grep -q 'ALL PASS' || say "guard self-test did not report ALL PASS"

order() { grep -oE "^[[:space:]]*stage\('[^']*'" "$1" | sed -E "s/^[[:space:]]*stage\('//; s/'$//"; }
idx() { order "$1" | grep -nFx "$2" | head -1 | cut -d: -f1; }
guard_body() {
  awk -v s="stage('Permitted commit guard')" 'index($0,s){p=1; next} p && /^[[:space:]]*stage\(/{exit} p' "$1"
}

for f in Jenkinsfile Jenkinsfile.deploy; do
  grep -q "string(name: 'PERMITTED_SHA', defaultValue: '', trim: true" "$f" || say "$f: PERMITTED_SHA parameter missing or not declared with defaultValue: '' and trim: true"
  grep -q "disableRestartFromStage()" "$f" || say "$f: options {} lacks disableRestartFromStage()"
  [ -n "$(idx "$f" 'Permitted commit guard')" ] || say "$f: no 'Permitted commit guard' stage"
  b="$(guard_body "$f")"
  printf '%s' "$b" | grep -q "sh(returnStatus: true, script: 'bash scripts/jenkins/permitted-sha-guard.sh')" || say "$f: guard stage does not run the guard script with returnStatus: true"
  printf '%s' "$b" | grep -q 'error(' || say "$f: guard stage does not error()"
  printf '%s' "$b" | grep -qE 'catchError|warnError|unstable\(' && say "$f: guard stage swallows the failure"
done

# Jenkinsfile: Package < guard < Image < Deploy + verify (dev); nothing checks out after the guard.
g=$(idx Jenkinsfile 'Permitted commit guard'); p=$(idx Jenkinsfile 'Package'); i=$(idx Jenkinsfile 'Image'); d=$(idx Jenkinsfile 'Deploy + verify (dev)')
[ "$p" -lt "$g" ] && [ "$g" -lt "$i" ] && [ "$i" -lt "$d" ] || say "Jenkinsfile: stage order must be Package < Permitted commit guard < Image < Deploy + verify (dev) (got P=$p G=$g I=$i D=$d)"
awk '/stage\(.Permitted commit guard.\)/{p=1} p' Jenkinsfile | grep -qE 'checkout\(|checkout scm' && say "Jenkinsfile: a checkout follows the guard"
# The downstream rollout is bound: DEPLOY_PERMITTED_SHA validated and forwarded as PERMITTED_SHA.
grep -q "string(name: 'DEPLOY_PERMITTED_SHA', defaultValue: '', trim: true" Jenkinsfile || say "Jenkinsfile: DEPLOY_PERMITTED_SHA parameter missing"
grep -q "dsha.matches('^\[0-9a-f\]{40}\$')" Jenkinsfile || say "Jenkinsfile: DEPLOY_PERMITTED_SHA is not validated before the downstream trigger"
grep -q "string(name: 'PERMITTED_SHA', value: dsha)" Jenkinsfile || say "Jenkinsfile: service-deploy trigger does not forward PERMITTED_SHA"

# Jenkinsfile.deploy: Checkout (main only) < guard < Install Contracts.
c=$(idx Jenkinsfile.deploy 'Checkout (main only)'); g=$(idx Jenkinsfile.deploy 'Permitted commit guard'); n=$(idx Jenkinsfile.deploy 'Install Contracts')
[ "$c" -lt "$g" ] && [ "$g" -lt "$n" ] || say "Jenkinsfile.deploy: stage order must be Checkout (main only) < Permitted commit guard < Install Contracts (got C=$c G=$g I=$n)"

[ "$fail" -eq 0 ] && echo "OK: both Jenkinsfiles carry the permitted-commit guard before their first effect"
exit "$fail"
