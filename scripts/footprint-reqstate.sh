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

grep -qF "$BEGIN" "$DOC" || { echo "FATAL: $DOC has no $BEGIN marker" >&2; exit 1; }
grep -qF "$END"   "$DOC" || { echo "FATAL: $DOC has no $END marker" >&2; exit 1; }

tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
table > "$tmp/table.md"

# Rebuild the document: everything before the marker, the fixed prose, the generated table, the
# fixed trailer, everything after. The prose lives HERE so the generated part is only the table.
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
