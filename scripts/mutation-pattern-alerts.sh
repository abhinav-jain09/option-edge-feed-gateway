#!/usr/bin/env bash
# Mutation check of the gateway half of the server-side pattern break alerts (market-structure-service/docs/PATTERN-ALERTS-SERVER-SIDE-DESIGN.md 9.3, gateway list, and the
# gateway's other rules): for each mutant ONE line of the relay, the resume handling, the audit read or the wiring is neutralised and the guarding tests MUST fail; a mutant that
# survives (or cannot be judged) is a missing test and fails the script. The mutants are in scripts/mutation-pattern-alerts.mutants (name, file, old text that must occur exactly
# once, new text; tab separated, validated below).
#
# Run from the repository root of a CLEAN checkout (it refuses to start otherwise; it edits source files and restores each one from a copy it takes first, also on INT/TERM/HUP):
#   scripts/mutation-pattern-alerts.sh
#   ONLY='AUDIT_|CURSOR_' scripts/mutation-pattern-alerts.sh     # a regex: run only the mutants whose name matches (default all; it must select at least one)
# A kill is counted only when the mutant COMPILES and the Surefire reports show that the guarding tests really ran and at least one failed or errored; any other build
# problem is "not judged" and fails the script. Test output goes to a log file in full (nothing is piped through head or tail), and mvn never reads the mutant list.
set -u
cd "$(git rev-parse --show-toplevel)" || exit 2
TESTS='PatternAlert*Test,ChartHelloBudgetTest,BreakHelloBudgetTest'
MUTANTS=scripts/mutation-pattern-alerts.mutants
ONLY=${ONLY:-.}
REPORTS=target/surefire-reports

if ! git diff --quiet HEAD -- src scripts || [ -n "$(git ls-files --others --exclude-standard -- src scripts)" ]; then
  echo "The working tree is not clean (src/ or scripts/ differ from HEAD): commit or stash first, so a restore can never lose work"; exit 2
fi

work=$(mktemp -d) || exit 2
touched=()
cleaned=0
cleanup() {   # put every file back exactly as it was before the first mutation; a failure is fatal and loud
  [ "$cleaned" -eq 1 ] && return 0
  cleaned=1
  local f ok=0
  for f in "${touched[@]+"${touched[@]}"}"; do
    if ! cp "$work/$(echo "$f" | tr '/' '_').orig" "$f"; then echo "FATAL: could not restore $f (copy in $work)"; ok=1; fi
  done
  [ "$ok" -eq 0 ] && rm -rf "$work"
  return "$ok"
}
trap 'cleanup; exit 130' INT
trap 'cleanup; exit 143' TERM
trap 'cleanup; exit 129' HUP
trap 'rc=$?; cleanup || rc=3; exit $rc' EXIT

# the manifest: exactly four tab separated fields per mutant (the last may be empty), unique names, the documented \n escape only; one file per field in $work/m
if ! python3 - "$MUTANTS" "$work/m" "$ONLY" <<'PY'
import os, re, sys
manifest, out, only = sys.argv[1:4]
try:
    only_re = re.compile(only)   # the one dialect that is both validated and applied
except re.error as exc:
    sys.exit("ONLY is not a valid regular expression: %s" % exc)
os.makedirs(out)
names = set()
selected = 0
for lineno, raw in enumerate(open(manifest, encoding='utf-8'), 1):
    line = raw.rstrip('\n')
    if not line or line.startswith('#'):
        continue
    f = line.split('\t')
    if len(f) != 4 or not f[0] or not f[1] or not f[2]:
        sys.exit("manifest line %d: expected NAME, FILE, OLD, NEW separated by tabs" % lineno)
    if f[0] in names:
        sys.exit("manifest line %d: duplicate mutant name %s" % (lineno, f[0]))
    names.add(f[0])
    if not only_re.search(f[0]):
        continue
    for i, v in enumerate(f):
        if i >= 2:
            v = v.replace('\\n', '\n')
        open(os.path.join(out, '%03d.%d' % (selected, i)), 'w', encoding='utf-8').write(v)
    selected += 1
if selected == 0:
    sys.exit("ONLY=%r selects no mutant" % only)
print(selected)
PY
then
  rm -rf "$work"; exit 2
fi
count=$(ls "$work/m" | grep -c '\.0$')

# a command with a hard time limit (macOS has no timeout(1)): its output goes to a log file in full, its whole process group is killed on expiry, the status is 124 then
bounded() {   # bounded SECONDS LOGFILE command...
  python3 - "$@" <<'PY'
import os, signal, subprocess, sys
limit, log = int(sys.argv[1]), sys.argv[2]
with open(log, 'wb') as out:
    p = subprocess.Popen(sys.argv[3:], stdout=out, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL, start_new_session=True)
    try:
        sys.exit(p.wait(timeout=limit))
    except subprocess.TimeoutExpired:
        os.killpg(p.pid, signal.SIGKILL)
        p.wait()
        sys.exit(124)
PY
}
LIMIT=900

# surefire totals of the executed test classes: "tests failures errors"
totals() {
  python3 - "$REPORTS" <<'PY'
import glob, sys, xml.etree.ElementTree as ET
t = f = e = 0
for p in glob.glob(sys.argv[1] + '/TEST-*.xml'):
    r = ET.parse(p).getroot()
    t += int(r.get('tests', 0)); f += int(r.get('failures', 0)); e += int(r.get('errors', 0))
print(t, f, e)
PY
}

# the baseline must be green and must really run tests, or every mutant would look killed
rm -rf "$REPORTS"
if ! bounded "$LIMIT" "$work/baseline.log" mvn -q -o test -Dtest="$TESTS"; then
  echo "BASELINE RED: the guarding tests fail on the unmutated sources (see $work/baseline.log); nothing was mutated"; exit 2
fi
read -r bt bf be < <(totals)
if [ "${bt:-0}" -lt 50 ] || [ "${bf:-1}" -ne 0 ] || [ "${be:-1}" -ne 0 ]; then
  echo "BASELINE NOT TRUSTED: tests=$bt failures=$bf errors=$be"; exit 2
fi
echo "baseline: $bt tests green"

killed=0; survived=0; notjudged=0
for ((i = 0; i < count; i++)); do
  id=$(printf '%03d' "$i")
  name=$(cat "$work/m/$id.0"); file=$(cat "$work/m/$id.1")
  orig="$work/$(echo "$file" | tr '/' '_').orig"
  if [ ! -f "$orig" ]; then
    cp "$file" "$orig" || { echo "FATAL: cannot back up $file"; exit 3; }
    touched+=("$file")
  fi
  if ! python3 - "$file" "$work/m/$id.2" "$work/m/$id.3" <<'PY'
import sys
f, oldf, newf = sys.argv[1:4]
old = open(oldf, encoding='utf-8').read()
new = open(newf, encoding='utf-8').read()
s = open(f, encoding='utf-8').read()
if s.count(old) != 1:
    sys.exit(1)
open(f, 'w', encoding='utf-8').write(s.replace(old, new))
PY
  then
    echo "MUTATION NOT APPLICABLE: $name"; notjudged=$((notjudged+1))
    cp "$orig" "$file" || { echo "FATAL: cannot restore $file"; exit 3; }
    continue
  fi
  if ! bounded "$LIMIT" "$work/compile.log" mvn -q -o test-compile; then
    echo "DID NOT COMPILE (not judged): $name"; notjudged=$((notjudged+1))
  else
    rm -rf "$REPORTS"
    bounded "$LIMIT" "$work/mut.log" mvn -q -o test -Dtest="$TESTS"
    status=$?
    read -r t f e < <(totals)
    if [ "$status" -eq 124 ]; then
      echo "TIMED OUT after ${LIMIT}s (not judged): $name"; notjudged=$((notjudged+1))
    elif [ "${t:-0}" -lt 50 ]; then
      echo "TESTS DID NOT RUN (not judged): $name (tests=$t, mvn exit $status)"; notjudged=$((notjudged+1))
    elif [ $((f + e)) -gt 0 ] && [ "$status" -ne 0 ]; then
      echo "killed:   $name ($((f + e)) failing of $t)"; killed=$((killed+1))
    elif [ "$status" -eq 0 ] && [ $((f + e)) -eq 0 ]; then
      echo "SURVIVED: $name"; survived=$((survived+1))
    else
      echo "INCONSISTENT RUN (not judged): $name (mvn exit $status, failures=$f errors=$e)"; notjudged=$((notjudged+1))
    fi
  fi
  cp "$orig" "$file" || { echo "FATAL: cannot restore $file"; exit 3; }
done
echo "killed=$killed survived=$survived notjudged=$notjudged"
[ "$survived" -eq 0 ] && [ "$notjudged" -eq 0 ]
