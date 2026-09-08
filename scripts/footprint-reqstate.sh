#!/usr/bin/env bash
# Regenerate §2a of ES-FOOTPRINT-GATEWAY-DESIGN.md IN PLACE from the checked-in campaign record.
#
# It rewrites the block between the BEGIN/END markers and leaves everything else byte-identical, so
# running it is the whole update: an earlier version of this script only printed to stdout while the
# document claimed it regenerated the section, which is a claim the script could not keep.
#
# --check exits non-zero if the committed section differs from what the record produces, so CI can
# hold the document to its own record.
set -euo pipefail
cd "$(dirname "$0")/.."

DOC=ES-FOOTPRINT-GATEWAY-DESIGN.md
BEGIN='<!-- BEGIN footprint-reqstate'
END='<!-- END footprint-reqstate -->'

table() {
  python3 scripts/footprint-reqstate.py \
    ES-FOOTPRINT-CAMPAIGN.json \
    "$DOC" \
    'G-R\d+[a-z]?' \
    '{"G-R.*":"2"}' \
    '{"G-R1|G-R2":"Flag and wiring","G-R3|G-R4|G-R5":"Delivery and views","G-R6":"Hello","G-R7":"Backfill routes","G-R8|G-R8a":"Deployment contingency","G-R9":"Metrics","G-R10":"Non-interference","G-R11":"Tests"}' \
    '{"G-R11":"TEST INVENTORY: this requirement lists the tests the others are held by, so it has no production clause a mutation could break"}'
}

# EXACTLY one marker pair, BEGIN before END, each at the start of its own line. A second pair, a
# reversed pair, or a marker mentioned inside prose would let the rewrite duplicate the generated
# block or swallow an unrelated section of the document.
nb=$(grep -cE "^${BEGIN}" "$DOC" || true)
ne=$(grep -cE "^${END}$"  "$DOC" || true)
[ "$nb" = "1" ] || { echo "FATAL: $DOC has $nb BEGIN markers at line start; exactly one is required" >&2; exit 1; }
[ "$ne" = "1" ] || { echo "FATAL: $DOC has $ne END markers at line start; exactly one is required" >&2; exit 1; }
lb=$(grep -nE "^${BEGIN}" "$DOC" | cut -d: -f1)
le=$(grep -nE "^${END}$"  "$DOC" | cut -d: -f1)
[ "$lb" -lt "$le" ] || { echo "FATAL: in $DOC the END marker (line $le) precedes BEGIN (line $lb)" >&2; exit 1; }

tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
table > "$tmp/table.md"

# Rebuild the document: everything before the marker, then the section heading, then the prose from
# scripts/footprint-reqstate.preamble (hand-maintained — it explains the table and is reviewed like
# any other prose), then the generated table, then everything from the END marker on. Only the table
# is derived from the record; the preamble is not, and is kept in its own file so that is visible.
awk -v tablefile="$tmp/table.md" -v begin="$BEGIN" -v end="$END" '
  index($0, begin) == 1 { skipping = 1; print; print ""; print "## 2a. Conformance — what a test actually holds"; print "";
      while ((getline line < "scripts/footprint-reqstate.preamble") > 0) print line
      close("scripts/footprint-reqstate.preamble")
      print ""
      while ((getline line < tablefile) > 0) print line
      close(tablefile)
      print ""
      next }
  index($0, end) == 1 { skipping = 0 }
  !skipping { print }
' "$DOC" > "$tmp/doc.md"

if [ "${1:-}" = "--check" ]; then
  if ! diff -q "$DOC" "$tmp/doc.md" >/dev/null; then
    echo "§2a is stale: regenerate with scripts/footprint-reqstate.sh" >&2
    diff -u "$DOC" "$tmp/doc.md" | head -60 >&2
    exit 1
  fi
  echo "§2a matches the campaign record"
  exit 0
fi

mv "$tmp/doc.md" "$DOC"
echo "§2a regenerated in $DOC"
