#!/usr/bin/env bash
# Erzeugt alle STL-Druckteile der v3 (linke Varianten + gemeinsame Teile).
# Aufruf aus diesem Ordner: ./export_stl_v3.sh   (rechte Varianten: SIDE=right)
set -euo pipefail
U="pinewood_u_v3.scad"
HOUSING="gehaeuse_v3.scad"
OUT="stl"
HAND="${SIDE:-left}"
OPENSCAD_BIN="${OPENSCAD_BIN:-openscad}"
mkdir -p "$OUT"

u() { "$OPENSCAD_BIN" -o "$OUT/$1.stl" -D "variant=\"$2\"" -D "piece=\"$3\"" -D "segment=$4" -D "side=$5" "$U"; }

# Start: Rückschale 3 Segmente, Vorderschale 3 Segmente (versetzt geteilt).
for side in 0 1; do for seg in 0 1 2; do
  u "start_${HAND}_schale${side}_seg${seg}" "start_${HAND}" half $seg $side
done; done
# Ziel: je Schale 2 Segmente (versetzt geteilt).
for side in 0 1; do for seg in 0 1; do
  u "finish_${HAND}_schale${side}_seg${seg}" "finish_${HAND}" half $seg $side
done; done
# Start-Fuß (nur Start), Hülsen, Klammern.
u "start_${HAND}_fuss" "start_${HAND}" start_foot 0 0
u "huelse_m4" "start_left" sleeve 0 0
u "spine_clip" "start_left" spine_clip 0 0
u "arm_clip" "start_left" arm_clip 0 0
# Probedrucke
u "test_sensortasche" "start_left" fit_sensor 0 0
u "test_schwalbenschwanz" "start_left" fit_joint 0 0
# Gehäuse
"$OPENSCAD_BIN" -o "$OUT/gehaeuse_base.stl" -D 'piece="base"' "$HOUSING"
"$OPENSCAD_BIN" -o "$OUT/gehaeuse_lid.stl" -D 'piece="lid"' "$HOUSING"
"$OPENSCAD_BIN" -o "$OUT/dock_lock.stl" -D 'piece="lock"' "$HOUSING"
echo "Fertig. STL-Dateien liegen in: $OUT/"
