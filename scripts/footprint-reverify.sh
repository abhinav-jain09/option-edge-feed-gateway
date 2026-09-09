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
SPEC="${1:-scripts/footprint-campaign.spec.json}"
RECORD="${2:-ES-FOOTPRINT-CAMPAIGN.json}"
[ -r "$SPEC" ]   || { echo "no campaign spec at $SPEC" >&2; exit 2; }
[ -r "$RECORD" ] || { echo "no campaign record at $RECORD" >&2; exit 2; }

# a temp DIRECTORY, not a temp file: mktemp creates the file, and the harness treats an
# existing output file as a campaign to resume — an empty one is not JSON.
TMPD=$(mktemp -d); trap 'rm -rf "$TMPD"' EXIT
OUT="$TMPD/record.json"
echo "re-running $(python3 -c "import json,sys; print(len(json.load(open(sys.argv[1]))['mutations']))" "$SPEC") mutations from $SPEC"
FOOTPRINT_ROOT="$ROOT" python3 scripts/footprint-mutate.py "$SPEC" "$OUT"

python3 - "$RECORD" "$OUT" <<'PY'
import json, sys
was, now = (json.load(open(p)) for p in sys.argv[1:3])
bad = []
for k in sorted(set(was) | set(now)):
    if k not in was: bad.append(f"{k}: the re-run produced a mutation the record does not have"); continue
    if k not in now: bad.append(f"{k}: the record has a mutation the re-run did not produce"); continue
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
print(f"the record reproduces: {len(was)} mutations, same status and same kill for every one")
PY
