#!/usr/bin/env bash
# Re-run the recorded campaign and prove the committed record reproduces.
#
# This is what the "pinned" column actually rests on. ES-FOOTPRINT-CAMPAIGN.json is a file: every
# refusal in scripts/footprint-reqstate.py checks the record against itself or against the source
# it names, and no amount of that makes a file tamper-evident. A record whose baseline block was
# invented wholesale is internally consistent and always will be. What cannot be invented is the
# run — so the record's authority is that anyone can reproduce it, and this script is how.
#
# Re-runs every mutation in the spec against THIS checkout and compares two things: WHAT each row
# claims — its requirement, quoted obligation, file, occurrence and patch, all of which come from
# the committed spec — and WHETHER the outcome reproduces: the status, the assertion failures each
# kill names, and the throw a killedByThrow record names. Any difference is reported and fails.
# Timings, hashes, output tails and source positions are expected to differ and are not compared.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$PWD
# This repository's campaign is TWO runs with two different runners — the page's node suite and
# the Java proxies/nav tests — and the record is their union. Re-running only one of them would
# report every mutation of the other as missing, so both specs run unless one is named.
RECORD="ES-FOOTPRINT-CAMPAIGN.json"
# Every run is the gate unless it says otherwise. The document's Coverage column and its list of
# not-re-run requirements come from scripts/footprint-gated-specs, and a committed file cannot know
# what CI actually does — so the check happens HERE, in the run: the specs this invocation ran must
# be exactly the ones that file declares.
#
# The default is deliberately this way round. Making it opt-in put the enforcement behind a flag,
# and deleting a flag from a workflow is the easiest way for the declaration and the real gate to
# drift apart with nothing going red. `--not-the-gate` is for a person re-running one spec by hand,
# and it says so at the point of use.
AS_THE_GATE=1
if [ "${1:-}" = "--as-the-gate" ]; then shift; fi
if [ "${1:-}" = "--not-the-gate" ]; then
    # ...but not in CI. The opt-out exists for a person re-running by hand, and a CI job that used
    # it would be the whole drift this check exists to stop, wearing the gate's name: the document
    # would go on claiming a coverage while the build re-ran something else. Automation does not
    # get the manual escape hatch.
    if [ -n "${GITHUB_ACTIONS:-}" ] || [ -n "${JENKINS_URL:-}" ] || [ "${CI:-}" = "true" ]; then
        echo "--not-the-gate is for a person re-running by hand; this is CI, where the run IS the gate." >&2
        echo "Change scripts/footprint-gated-specs if the coverage is meant to be different." >&2
        exit 2
    fi
    AS_THE_GATE=; shift
fi

if [ -n "$AS_THE_GATE" ]; then
    # The gate takes NO arguments. Its scope is scripts/footprint-gated-specs and nothing else.
    #
    # Letting it name a spec was a hole in two directions. A run naming the declared spec passed the
    # spec-set check but became a PARTIAL run, which stops asking whether the record holds rows no
    # spec declares — and a row sharing a gated command would then render as re-run by the gate
    # while appearing in no spec at all. A second argument could point the run at a different
    # record, so the gate would reverify one file while `--check` rendered another.
    [ $# -eq 0 ] || {
        echo "the gate takes no arguments: it re-runs exactly what scripts/footprint-gated-specs" >&2
        echo "declares, against $RECORD. Pass --not-the-gate to re-run a single spec by hand." >&2
        exit 2; }
    [ -r scripts/footprint-gated-specs ] || {
        echo "scripts/footprint-gated-specs does not exist, so nothing says what this gate covers." >&2
        echo "Create it, or pass --not-the-gate if this is a person re-running by hand." >&2
        exit 2; }
    # `grep -v '^label:'`: the declaration may carry metadata, and comparing that against the spec
    # list made the gate fail before it ran a single mutation.
    SPECS=()
    while IFS= read -r spec; do [ -n "$spec" ] && SPECS+=("$spec"); done < <(
        sed 's/#.*//' scripts/footprint-gated-specs | tr -d '[:blank:]' \
        | grep -v '^$' | grep -v '^label:' | sort)
    [ ${#SPECS[@]} -gt 0 ] || { echo "scripts/footprint-gated-specs declares no spec" >&2; exit 2; }
    for spec in "${SPECS[@]}"; do
        [ -r "$spec" ] || { echo "scripts/footprint-gated-specs names $spec, which does not exist" >&2; exit 2; }
    done
else
    if [ $# -gt 0 ]; then SPECS=("$1"); [ $# -gt 1 ] && RECORD="$2"; fi
    if [ $# -eq 0 ]; then
        # Every committed spec, discovered rather than listed: a hard-coded pair was copied into a
        # repository that has one spec, and named a file that does not exist there.
        SPECS=()
        for s in scripts/footprint-campaign*.json; do [ -r "$s" ] && SPECS+=("$s"); done
        [ ${#SPECS[@]} -gt 0 ] || { echo "no scripts/footprint-campaign*.json to re-run" >&2; exit 2; }
    fi
fi
for s in "${SPECS[@]}"; do [ -r "$s" ] || { echo "no campaign spec at $s" >&2; exit 2; }; done
[ -r "$RECORD" ] || { echo "no campaign record at $RECORD" >&2; exit 2; }

# a temp DIRECTORY, not a temp file: mktemp creates the file, and the harness treats an
# existing output file as a campaign to resume — an empty one is not JSON.
TMPD=$(mktemp -d); trap 'rm -rf "$TMPD"' EXIT
OUT="$TMPD/record.json"
for SPEC in "${SPECS[@]}"; do
    echo "re-running $(python3 -c "import json,sys; print(len(json.load(open(sys.argv[1]))['mutations']))" "$SPEC") mutations from $SPEC"
    FOOTPRINT_ROOT="$ROOT" python3 scripts/footprint-mutate.py "$SPEC" "$TMPD/part-$(basename "$SPEC").json"
done
python3 - "$OUT" "$TMPD" <<'MERGE'
import glob, json, sys
out, tmpd = sys.argv[1], sys.argv[2]
merged = {}
for p in sorted(glob.glob(tmpd + '/part-*.json')):
    merged.update(json.load(open(p)))
json.dump(merged, open(out, 'w'), indent=1)
MERGE

python3 - "$RECORD" "$OUT" "${SPECS[@]}" <<'COMPARE'
import glob, json, sys
record, rerun = sys.argv[1], sys.argv[2]
specs = sys.argv[3:]
was, now = json.load(open(record)), json.load(open(rerun))
# Only the mutations the specs just re-ran are compared. Naming one spec of a repository whose
# campaign is two runs would otherwise report the other run's mutations as missing from the re-run.
# When every spec ran, the record may not hold anything beyond them either.
expected = set()
for p in specs:
    expected |= {m['key'] for m in json.load(open(p))['mutations']}
# A row the record holds that NO committed spec declares is a row nothing can ever re-run, and that
# is true whatever this invocation happened to run. Checked against every spec in the repository,
# not against the ones just run — otherwise a gate whose declared set is one of two specs reports
# the other spec's rows as undeclared, which they are not.
everywhere = set()
for p in sorted(glob.glob('scripts/footprint-campaign*.json')):
    try:
        everywhere |= {m['key'] for m in json.load(open(p))['mutations']}
    except Exception:
        pass
bad = []
for k in sorted(set(was) - everywhere):
    bad.append(f"{k}: the record holds a mutation no committed spec declares")
for k in sorted(expected):
    if k not in was:
        bad.append(f"{k}: the spec declares a mutation the record does not have"); continue
    if k not in now:
        bad.append(f"{k}: the re-run produced no result for a mutation it was given"); continue
    a, b = was[k], now[k]
    # WHAT the row claims, before whether the outcome reproduces. Comparing only status and kill let
    # a record keep a genuine key and a genuine failure while its requirement, quoted obligation,
    # file or patch were re-pointed at something else: the re-run reproduces the spec's mutation and
    # agrees about the outcome, and the table then attributes that outcome to a clause nobody broke.
    # These fields all come from the committed spec, so the spec is what each row is held to.
    #
    # `line` and `enclosing` are deliberately NOT compared: they are positions in the source, and
    # this script is meant to be run against a later checkout where they legitimately move.
    identity = ('requirement', 'clause', 'documentText', 'file', 'occurrence',
                'occurrencesInFile', 'command', 'patch')
    differing = [fld for fld in identity if a.get(fld) != b.get(fld)]
    if differing:
        for fld in differing:
            bad.append(f"{k}: the record's {fld} is not the spec's ({a.get(fld)!r} vs {b.get(fld)!r})")
    elif a['status'] != b['status']:
        bad.append(f"{k}: recorded {a['status']}, re-ran {b['status']}")
    elif sorted(a.get('assertionFailures') or []) != sorted(b.get('assertionFailures') or []):
        bad.append(f"{k}: recorded kills {a.get('assertionFailures')}, re-ran {b.get('assertionFailures')}")
    elif (a.get('killedByThrow') or {}) != (b.get('killedByThrow') or {}):
        bad.append(f"{k}: recorded throw {a.get('killedByThrow')}, re-ran {b.get('killedByThrow')}")
if bad:
    print("THE RECORD DOES NOT REPRODUCE:"); [print("  ", p) for p in bad]; sys.exit(1)
print(f"the record reproduces: {len(expected)} mutations, same claim and same outcome for every one")
COMPARE
