#!/usr/bin/env bash
# Exports all v3 print parts (left and right variants plus shared parts) to stl/.
# Run from this folder: ./export_stls_v3.sh
set -euo pipefail
U="pinewood_u_v3.scad"
HOUSING="electronics_housing_v3.scad"
OUT="stl"
OPENSCAD_BIN="${OPENSCAD_BIN:-openscad}"
mkdir -p "$OUT"

u() { "$OPENSCAD_BIN" -o "$OUT/$1.stl" -D "variant=\"$2\"" -D "piece=\"$3\"" -D "segment=$4" -D "side=$5" "$U"; }
shell_name() { [ "$1" = 0 ] && echo back_shell || echo front_shell; }

for hand in left right; do
  # Start: back and front shell, 3 segments each (staggered splits).
  for side in 0 1; do for seg in 0 1 2; do
    u "start_${hand}_$(shell_name $side)_seg$((seg+1))" "start_${hand}" half $seg $side
  done; done
  # Finish: back and front shell, 2 segments each (staggered splits).
  for side in 0 1; do for seg in 0 1; do
    u "finish_${hand}_$(shell_name $side)_seg$((seg+1))" "finish_${hand}" half $seg $side
  done; done
  # Start foot (start only).
  u "start_${hand}_foot" "start_${hand}" start_foot 0 0
done
# Shared parts (not handed).
u "spine_clip" "start_left" spine_clip 0 0
u "arm_clip" "start_left" arm_clip 0 0
"$OPENSCAD_BIN" -o "$OUT/housing_base.stl" -D 'piece="base"' "$HOUSING"
"$OPENSCAD_BIN" -o "$OUT/housing_lid.stl" -D 'piece="lid"' "$HOUSING"
"$OPENSCAD_BIN" -o "$OUT/dock_lock.stl" -D 'piece="lock"' "$HOUSING"
# Test prints.
u "test_sensor_seat" "start_left" fit_sensor 0 0
u "test_dovetail" "start_left" fit_joint 0 0
echo "Done. STL files are in: $OUT/"
