# Pinewood Derby Break-Beam Gate CAD

This folder is the maintained source package for a two-lane Pinewood Derby infrared break-beam gate. All dimensions are in millimetres and all source comments are in English.

The design deliberately separates the structural U-frame, removable cable covers, and electronics housing. That keeps parts printable without excessive support material and lets the electronics housing be replaced independently.

## Package contents

| File | Purpose |
| --- | --- |
| `pinewood_u_frame.scad` | Main parametric U-frame: start and finish variants, left/right mirrors, cable channels, sensor pockets, covers, and 250 mm printer split joints. |
| `electronics_housing_40mm_entry.scad` | Separate glue-on electronics housing with board supports, display opening, USB opening, cable pass-through, and sliding lid. |
| `breakbeam_sensor_holder_test.scad` | Small isolated test model for the sensor pocket, optical slot, cable channel, and cover fit. Print this first when changing sensor-related dimensions. |
| `clip_on_cable_covers.scad` | Supplemental external clip-on covers with tapered 2 mm to 3.5 mm legs; it does not modify the original U-frame. |
| `export_all_stls.sh` | Exports all required U-frame, cover, and housing STL files. |
| `export_housing_stls.sh` | Exports only the two housing STL files. |
| `export_clip_on_covers.sh` | Exports only the supplemental clip-on cover STL files. |

## Coordinate system and design intent

### U-frame

`pinewood_u_frame.scad` uses:

- `+X`: open side of the U-frame / track side.
- `X=0`: rear face of the tall rail; this is where the concealed cable channel and glue-on housing are located.
- `Y`: lateral width of the 20 mm square profile.
- `Z`: vertical direction.

The U opening stays clear. The break-beam modules are mounted from the outer cable-channel side. Only their optical openings are visible from inside the U-frame. The optical opening is a 15.4 x 7.7 mm slot, extending only back towards the rail; the end of each arm retains a solid 7 mm section.

### Electronics housing

`electronics_housing_40mm_entry.scad` uses:

- `Y=0`: flat glue face against the rear mounting plate of the U-frame.
- `+Y`: outside / display-lid side.
- `X`: housing width.
- `Z`: housing height.

The housing cable pass-through is 12 x 40 mm and aligns with the open central segment of the U-frame cable channel. The lid slides from left to right in integrated grooves; its entry side is deliberately open so no fragile bridge is printed across the entry.

## Default dimensions

### Frame and sensor system

| Parameter | Default | Meaning |
| --- | ---: | --- |
| `profile` | 20 | Square U-frame profile size. |
| `arm` | 105 | Length of each upper/lower arm. |
| Start height | 340 | Overall start-frame height. |
| Finish height | 240 | Overall finish-frame height. |
| `channel_depth` | 10 | Recessed cable-channel depth. |
| `sensor_extra` | 4 | Additional depth for the sensor pocket. |
| Sensor body | 19.8 x 9.9 x 7.9 | Sensor dimensions assumed by the pocket. |
| Optic slot | 15.4 x 7.7 | Through-slot visible from inside the U. |
| Solid arm tip | 7 | Material left beyond the sensor channel. |
| `mid_gap` | 40 | Open cable-entry section at the center of the rear rail. |
| `mount_width` | 60 | Width of the flat glue area for the housing. |

### Electronics housing

| Parameter | Default | Meaning |
| --- | ---: | --- |
| `box_width` | 95 | Housing width for rigid connectors and side cable space. Set to 70 for silicone-wire-only wiring. |
| `box_height` | 82 | Overall housing height. |
| `box_depth` | 48 | Overall housing depth. |
| `wall` | 3 | Housing wall thickness. |
| Board footprint | 64 x 63 | Board dimensions used by the supports. |
| `board_stack` | 17.8 | Board plus ESP/display assembly height. |
| Display opening | 27.8 x 50.8 | Vertical display opening with clearance. |
| Cable pass-through | 12 x 40 | Opening towards the U-frame cable channel. |
| Lid thickness | 2 | Sliding display lid thickness. |
| `lid_fit_clearance` | 0.2 | Extra PETG clearance for the sliding lid. Increase slightly if the lid is too tight. |

## Choosing variants and parts in OpenSCAD

Open either SCAD file in OpenSCAD. Change the variables at the top, press **F6** to render, then use **File → Export → Export as STL**.

### Main U-frame

Set `variant` to one of:

- `start_left`, `start_right`
- `finish_left`, `finish_right`

`*_right` mirrors the complete geometry. Use this only when the physical lane needs the mirrored orientation.

Set `piece` to one of:

- `preview`: assembled frame and covers for inspection.
- `frame`: complete one-piece frame; suitable for the 240 mm finish gate.
- `start_bottom`, `start_middle`, `start_top`: three interlocking sections for the 340 mm start gate on a 250 mm printer.
- `lower_arm_cover`, `upper_arm_cover`, `lower_vertical_cover`, `upper_vertical_cover`: individual cable covers.
- `print_layout`: all four covers laid flat for printing.

The start sections have keyed tongues and sockets. They must be assembled in their intended orientation; this preserves the 90° frame geometry and sensor alignment.

### Electronics housing

Set `piece` to:

- `base`: housing body only.
- `lid`: sliding display lid only.
- `print_layout`: both parts arranged for printing.
- `preview`: assembled inspection view.

## Generating STL files

Open a terminal in this folder and run:

```bash
chmod +x export_all_stls.sh export_housing_stls.sh export_clip_on_covers.sh
./export_all_stls.sh
```

The generated files are written to `stl/`. The full export contains:

- Six start-frame pieces: bottom, middle, and top for left and right.
- Two one-piece finish frames: left and right.
- Four covers for each frame height: lower/upper arm cover and lower/upper rail cover.
- One housing base and one housing display lid.
- Eight supplemental clip-on covers: two arm clips per frame type, two 80 mm finish outer clips, and two 130 mm start outer clips.

To export only the housing:

```bash
./export_housing_stls.sh
```

To export only the supplemental clip-on covers:

```bash
./export_clip_on_covers.sh
```

### Supplemental clip-on covers

`clip_on_cable_covers.scad` is an additive-only model. It is not referenced by,
and does not alter, `pinewood_u_frame.scad`. Every cover is a U-shaped friction
clip with a 21 mm clear inner width at the closed side, narrowing to 19.5 mm at
the open clipping side. Its 15 mm legs taper from 2 mm to 3.5 mm thickness and
provide the snap/friction fit over the 20 mm frame profile from the cable-duct
side.

Exported installation-specific parts are:

- `finish_clip_lower_arm` and `finish_clip_upper_arm`: one 105 mm clip for each arm of a finish U-frame. The final 20 mm has only the flat cover; its side legs stop after 85 mm.
- `finish_clip_outer_lower` and `finish_clip_outer_upper`: two 80 mm rear clips for a finish U-frame, each with both side legs over the full 80 mm length.
- `start_clip_lower_arm` and `start_clip_upper_arm`: one 105 mm clip for each arm of a start U-frame. The final 20 mm has only the flat cover; its side legs stop after 85 mm.
- `start_clip_outer_lower` and `start_clip_outer_upper`: two 130 mm rear clips for a start U-frame, each with both side legs over the full 130 mm length.

The clips are exported in print orientation, with their flat outer top face on
the print bed and their legs facing upward. They need no support. The 21 mm
clearance is intentionally a friction fit. If your PETG profile or printer
calibration makes it too tight, increase `clip_open_inner_width` in 0.1 mm steps.
`piece="print_layout"` displays all eight required physical clips separately
and fits them onto a 250 x 250 mm print bed.

The scripts expect `openscad` to be available on the command path. To use a different executable, set `OPENSCAD_BIN`:

```bash
OPENSCAD_BIN=/path/to/openscad ./export_all_stls.sh
```

## Recommended modification workflow

1. Change exactly one group of parameters at a time.
2. For sensor, channel, or cover-fit changes, first print `breakbeam_sensor_holder_test.scad` with `piece="outer_open"` and/or `piece="cover"`.
3. Check a `preview` in OpenSCAD before exporting production parts.
4. Export fresh STL files after every geometry change. Do not reuse an STL generated before the SCAD change.
5. For fit changes in PETG, adjust clearances in increments of 0.1 mm.

## Print and assembly notes

- Print the frame parts so their largest flat face rests on the bed whenever possible. The split start-frame parts were designed to fit a 250 mm build plate.
- Print covers flat. They are 2 mm thick and need no support.
- Print the housing base with its large, flat glue face on the bed where practical; print the lid with its flat face on the bed.
- Install the sensor modules from the outer cable channel. Route their wires through the concealed channels, then slide in the covers.
- Join start-frame sections with the keyed tongues/sockets. Dry-fit before gluing.
- Glue the housing to the 60 mm wide mounting plate centered over the 40 mm cable entry. Keep the 12 x 40 mm housing pass-through aligned with the rear cable channel.
- Insert the board on the six stepped supports, route cables through the available gaps, and then slide the display lid in from the open entry side.

## Important limitations and checks

- Verify physical dimensions against your exact sensor, board, ESP32, display, plugs, WAGO terminals, and cable bend radius before committing to a full print.
- The 95 mm housing is for rigid connector clearance. If you reduce it to 70 mm, re-check board centering, support placement, cable clearance, and display position.
- The models are parametric but use several derived positions. When changing a fundamental dimension such as `profile`, `arm`, `box_width`, or `board_width`, inspect `preview` and the physical clearances instead of assuming every dependent feature still fits.

## Maintenance rule for developers and AI agents

Keep the following invariants unless the project requirements explicitly change:

1. Nothing may protrude into the inner U opening except the optical slots.
2. Sensors load from the external cable-channel side; their optics face each other vertically.
3. The rear frame channel must stay continuous through the central 40 mm housing-entry area while the inner 10 mm of the 20 mm rail remains solid.
4. Frame and cover geometry must use matching C-groove dimensions.
5. Start-frame joints must remain keyed and preserve the 90° geometry.
6. The housing lid must slide freely from its open entry side and must not include a fragile bridge across that entry.
