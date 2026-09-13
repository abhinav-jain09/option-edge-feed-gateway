#!/usr/bin/env bash
# Both Jenkinsfiles carry the canonical permitted-commit guard before their first effect (Deployment
# Permission Rule): the shared validator (scripts/jenkins/validate-jenkinsfile-guard.py, byte-identical
# across the four repositories) judges them against scripts/jenkins/jenkins-permitted-sha-scope.txt, its
# mutation suite proves it refuses every demonstrated skip path, the downstream-definition helper and the guard's
# own suite run, and gateway-guard-test.py executes this repository's reproductions and image-lock path.
set -euo pipefail
cd "$(dirname "$0")/../.."
bash scripts/jenkins/permitted-sha-guard-test.sh | tail -1 | grep -q 'ALL PASS' || { echo "FAIL: guard self-test did not report ALL PASS"; exit 1; }
python3 scripts/jenkins/validate-jenkinsfile-guard-test.py | tail -1 | grep -q 'ALL PASS' || { echo "FAIL: validator mutation suite did not report ALL PASS"; exit 1; }
python3 scripts/jenkins/require-guarded-downstream-test.py | tail -1 | grep -q 'ALL PASS' || { echo "FAIL: downstream-definition suite did not report ALL PASS"; exit 1; }
python3 scripts/jenkins/gateway-guard-test.py | tail -1 | grep -q 'ALL PASS' || { echo "FAIL: gateway guard test did not report ALL PASS"; exit 1; }
for s in scripts/jenkins/*.sh; do bash -n "$s"; done
python3 scripts/jenkins/validate-jenkinsfile-guard.py --root . --manifest scripts/jenkins/jenkins-permitted-sha-scope.txt
