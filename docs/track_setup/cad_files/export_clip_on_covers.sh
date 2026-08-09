#!/usr/bin/env bash
# Exports all supplemental clip-on cable-cover STL files.
# Run from this directory: ./export_clip_on_covers.sh
set -euo pipefail

SCAD="clip_on_cable_covers.scad"
OUT="stl"
OPENSCAD_BIN="${OPENSCAD_BIN:-openscad}"
mkdir -p "$OUT"

export_part() {
  local name="$1" piece="$2"
  "$OPENSCAD_BIN" -o "$OUT/$name.stl" -D "piece=\"$piece\"" "$SCAD"
}

export_part "finish_clip_lower_arm" finish_lower_arm
export_part "finish_clip_upper_arm" finish_upper_arm
export_part "finish_clip_outer_full" finish_outer_full
export_part "start_clip_lower_arm" start_lower_arm
export_part "start_clip_upper_arm" start_upper_arm
export_part "start_clip_outer_lower" start_outer_lower
export_part "start_clip_outer_upper" start_outer_upper

echo "Done. STL files are in: $OUT/"

