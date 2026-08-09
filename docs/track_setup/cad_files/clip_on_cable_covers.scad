/*
  Supplemental clip-on covers for the Pinewood Derby U-frame cable ducts.

  These parts do not modify or replace any existing frame or sliding-cover
  geometry. They are external, U-shaped clips that can be pressed over a
  20 mm square U-frame profile to conceal a cable duct or an imperfect print.

  Cross-section (all dimensions in mm):
    - 21 mm clear inside width at the closed top side
    - 19 mm clear inside width at the open clipping side
    - 2 mm side-wall thickness at the top, tapering to 4 mm at the open end
    - 3 mm top wall and 15 mm side-leg drop

  The long axis of every clip is X. Print each clip with its flat outer top
  face on the build plate. No supports are required.
*/

piece = "print_layout";
// all | print_layout | finish_lower_arm | finish_upper_arm | finish_outer_full
// start_lower_arm | start_upper_arm | start_outer_lower | start_outer_upper

profile_size = 20;
clip_closed_inner_width = 21;
clip_open_inner_width = 19;
clip_top_wall = 3;
clip_side_wall_top = 2;
clip_side_wall_open = 4;
clip_leg_drop = 15;

arm_clip_length = 105;
finish_outer_clip_length = 240;
start_outer_clip_length = 150; // (340 mm start height - 40 mm device gap) / 2

// U-shaped cover with a top panel and two tapered side legs. The legs narrow
// the 21 mm closed-side clearance to 19 mm at the open clipping side, creating
// the required holding force over the 20 mm frame profile.
module clip_cover(length) {
  outer_width_top = clip_closed_inner_width + 2 * clip_side_wall_top;
  outer_width_open = clip_open_inner_width + 2 * clip_side_wall_open;
  outer_offset = (outer_width_open - outer_width_top) / 2;
  union() {
    // Flat outer top face.
    translate([0, 0, clip_leg_drop])
      cube([length, outer_width_top, clip_top_wall]);
    // Tapered side legs: 2 mm at the closed side, 4 mm at the open side.
    // Their outer faces move out by only 1 mm over 15 mm of height; the top
    // exterior remains a clean 90-degree surface.
    hull() {
      translate([0, -outer_offset, 0])
        cube([length, clip_side_wall_open, .01]);
      translate([0, 0, clip_leg_drop-.01])
        cube([length, clip_side_wall_top, .01]);
    }
    hull() {
      translate([0, outer_width_top-clip_side_wall_top, clip_leg_drop-.01])
        cube([length, clip_side_wall_top, .01]);
      translate([0, outer_width_top-clip_side_wall_top-outer_offset, 0])
        cube([length, clip_side_wall_open, .01]);
    }
  }
}

// Rotate the clip so its flat outer top face lies on the build plate and the
// two legs print upward. This avoids bridging across the 21 mm opening.
module print_oriented_clip(length) {
  translate([0, 0, clip_leg_drop + clip_wall])
    rotate([180, 0, 0]) clip_cover(length);
}

// Named parts are intentionally separate, even where their geometry is the
// same, so the generated STL names map directly to the installation position.
module finish_lower_arm() print_oriented_clip(arm_clip_length);
module finish_upper_arm() print_oriented_clip(arm_clip_length);
module finish_outer_full() print_oriented_clip(finish_outer_clip_length);
module start_lower_arm() print_oriented_clip(arm_clip_length);
module start_upper_arm() print_oriented_clip(arm_clip_length);
module start_outer_lower() print_oriented_clip(start_outer_clip_length);
module start_outer_upper() print_oriented_clip(start_outer_clip_length);

// One-at-a-time print layout. It contains one copy of each unique clip length.
// Export the named pieces for duplicate parts instead of using this layout.
module print_layout() {
  print_oriented_clip(arm_clip_length);
  translate([0, 35, 0]) print_oriented_clip(start_outer_clip_length);
  translate([0, 70, 0]) print_oriented_clip(finish_outer_clip_length);
}

if (piece == "all") {
  finish_lower_arm();
  finish_upper_arm();
  finish_outer_full();
  start_lower_arm();
  start_upper_arm();
  start_outer_lower();
  start_outer_upper();
}
if (piece == "print_layout") print_layout();
if (piece == "finish_lower_arm") finish_lower_arm();
if (piece == "finish_upper_arm") finish_upper_arm();
if (piece == "finish_outer_full") finish_outer_full();
if (piece == "start_lower_arm") start_lower_arm();
if (piece == "start_upper_arm") start_upper_arm();
if (piece == "start_outer_lower") start_outer_lower();
if (piece == "start_outer_upper") start_outer_upper();
