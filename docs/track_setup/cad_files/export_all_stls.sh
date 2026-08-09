#!/usr/bin/env bash
# Exports every individual STL print part from pinewood_u_frame.scad.
# Run from this directory: ./export_all_stls.sh
set -euo pipefail

SCAD="pinewood_u_frame.scad"
HOUSING_SCAD="electronics_housing_40mm_entry.scad"
CLIP_SCAD="clip_on_cable_covers.scad"
OUT="stl"
OPENSCAD_BIN="${OPENSCAD_BIN:-openscad}"
mkdir -p "$OUT"

export_part() {
  local name="$1" variant="$2" piece="$3"
  "$OPENSCAD_BIN" -o "$OUT/$name.stl" \
    -D "variant=\"$variant\"" -D "piece=\"$piece\"" "$SCAD"
}

export_housing() {
  local name="$1" piece="$2"
  "$OPENSCAD_BIN" -o "$OUT/$name.stl" \
    -D "piece=\"$piece\"" "$HOUSING_SCAD"
}

export_clip() {
  local name="$1" piece="$2"
  "$OPENSCAD_BIN" -o "$OUT/$name.stl" \
    -D "piece=\"$piece\"" "$CLIP_SCAD"
}

# Start gate: three interlocking frame sections per side.
for side in left right; do
  export_part "start_${side}_bottom" "start_${side}" start_bottom
  export_part "start_${side}_middle" "start_${side}" start_middle
  export_part "start_${side}_top" "start_${side}" start_top
done

# Finish gate: one-piece frame per side.
for side in left right; do
  export_part "finish_${side}_frame" "finish_${side}" frame
done

# Covers as individual parts. Upper and lower covers are exported separately,
# even though the two transverse-arm covers are geometrically identical.
for size in start finish; do
  export_part "${size}_cover_lower_arm" "${size}_left" lower_arm_cover
  export_part "${size}_cover_upper_arm" "${size}_left" upper_arm_cover
  export_part "${size}_cover_lower_rail" "${size}_left" lower_vertical_cover
  export_part "${size}_cover_upper_rail" "${size}_left" upper_vertical_cover
done

# Separate glue-on electronics housing and its display lid.
export_housing "housing_base_95mm" base
export_housing "housing_display_lid_95mm" lid

# Supplemental clip-on covers; these do not alter the original frame or covers.
export_clip "finish_clip_lower_arm" finish_lower_arm
export_clip "finish_clip_upper_arm" finish_upper_arm
export_clip "finish_clip_outer_full" finish_outer_full
export_clip "start_clip_lower_arm" start_lower_arm
export_clip "start_clip_upper_arm" start_upper_arm
export_clip "start_clip_outer_lower" start_outer_lower
export_clip "start_clip_outer_upper" start_outer_upper

echo "Done. STL files are in: $OUT/"
