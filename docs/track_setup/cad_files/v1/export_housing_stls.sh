#!/usr/bin/env bash
# Exports the two separate electronics-housing STL print parts.
# Run from this directory: ./export_housing_stls.sh
set -euo pipefail

SCAD="electronics_housing_40mm_entry.scad"
OUT="stl"
OPENSCAD_BIN="${OPENSCAD_BIN:-openscad}"
mkdir -p "$OUT"

"$OPENSCAD_BIN" -o "$OUT/housing_base_95mm.stl" \
  -D 'piece="base"' "$SCAD"
"$OPENSCAD_BIN" -o "$OUT/housing_display_lid_95mm.stl" \
  -D 'piece="lid"' "$SCAD"

echo "Done. STL files are in: $OUT/"
