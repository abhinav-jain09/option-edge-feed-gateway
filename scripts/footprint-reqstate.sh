#!/usr/bin/env bash
# Regenerate §2a of ES-FOOTPRINT-GATEWAY-DESIGN.md from the checked-in campaign record.
# Run from the repository root; the output replaces the block between the two markers.
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/footprint-reqstate.py \
  ES-FOOTPRINT-CAMPAIGN.json \
  ES-FOOTPRINT-GATEWAY-DESIGN.md \
  'G-R\d+[a-z]?' \
  '{"G-R.*":"2"}' \
  '{"G-R1|G-R2":"Flag and wiring","G-R3|G-R4|G-R5":"Delivery and views","G-R6":"Hello","G-R7":"Backfill routes","G-R8|G-R8a":"Deployment contingency","G-R9":"Metrics","G-R10":"Non-interference","G-R11":"Tests"}' \
  '{"G-R11":"TEST INVENTORY: this requirement lists the tests the others are held by, so it has no production clause a mutation could break"}'
