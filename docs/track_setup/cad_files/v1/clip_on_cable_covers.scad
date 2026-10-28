/*
  Supplemental clip-on covers for the Pinewood Derby U-frame cable ducts.

  These parts do not modify or replace any existing frame or sliding-cover
  geometry. They are external, U-shaped clips that can be pressed over a
  20 mm square U-frame profile to conceal a cable duct or an imperfect print.

  Cross-section (all dimensions in mm):
    - 21 mm clear inside width at the closed top side
    - 19.5 mm clear inside width at the open clipping side
    - 2 mm side-wall thickness at the top, tapering to 3.5 mm at the open end
    - 3 mm top wall and 15 mm side-leg drop

  The long axis of every clip is X. Print each clip with its flat outer top
  face on the build plate. No supports are required.
*/

piece = "print_layout";
// all | print_layout | finish_lower_arm | finish_upper_arm
// finish_outer_lower | finish_outer_upper | start_lower_arm | start_upper_arm
// start_outer_lower | start_outer_upper

profile_size = 20;
clip_closed_inner_width = 21;
clip_open_inner_width = 19.5;
clip_top_wall = 3;
clip_side_wall_top = 2;
clip_side_wall_open = 3.5;
clip_leg_drop = 15;

arm_clip_length = 105;
arm_clip_leg_length = 85;      // The final 20 mm is a flat cover only.
finish_outer_clip_length = 80;
start_outer_clip_length = 130;

// U-shaped cover with a top panel and two tapered side legs. The legs narrow
// the 21 mm closed-side clearance to 19.5 mm at the open clipping side, creating
// the required holding force over the 20 mm frame profile.
module clip_cover(length, leg_length=length) {
  outer_width_top = clip_closed_inner_width + 2 * clip_side_wall_top;
  outer_width_open = clip_open_inner_width + 2 * clip_side_wall_open;
  outer_offset = (outer_width_open - outer_width_top) / 2;
  union() {
    // Flat outer top face.
    translate([0, 0, clip_leg_drop])
      cube([length, outer_width_top, clip_top_wall]);
    // Tapered side legs: 2 mm at the closed side, 3.5 mm at the open side.
    // Their outer faces move out slightly over 15 mm of height; the top
    // exterior remains a clean 90-degree surface.
    hull() {
      translate([0, -outer_offset, 0])
        cube([leg_length, clip_side_wall_open, .01]);
      translate([0, 0, clip_leg_drop-.01])
        cube([leg_length, clip_side_wall_top, .01]);
    }
    hull() {
      translate([0, outer_width_top-clip_side_wall_top, clip_leg_drop-.01])
        cube([leg_length, clip_side_wall_top, .01]);
      translate([0, outer_width_top-clip_side_wall_top-outer_offset, 0])
        cube([leg_length, clip_side_wall_open, .01]);
    }
  }
}

// Rotate the clip so its flat outer top face lies on the build plate and the
// two legs print upward. This avoids bridging across the 21 mm opening.
module print_oriented_clip(length, leg_length=length) {
  translate([0, 0, clip_leg_drop + clip_top_wall])
    rotate([180, 0, 0]) clip_cover(length, leg_length);
}

// Named parts are intentionally separate, even where their geometry is the
// same, so the generated STL names map directly to the installation position.
module finish_lower_arm() print_oriented_clip(arm_clip_length, arm_clip_leg_length);
module finish_upper_arm() print_oriented_clip(arm_clip_length, arm_clip_leg_length);
// Rear clips must retain both side legs across their complete length.
module finish_outer_lower() print_oriented_clip(finish_outer_clip_length, finish_outer_clip_length);
module finish_outer_upper() print_oriented_clip(finish_outer_clip_length, finish_outer_clip_length);
module start_lower_arm() print_oriented_clip(arm_clip_length, arm_clip_leg_length);
module start_upper_arm() print_oriented_clip(arm_clip_length, arm_clip_leg_length);
module start_outer_lower() print_oriented_clip(start_outer_clip_length, start_outer_clip_length);
module start_outer_upper() print_oriented_clip(start_outer_clip_length, start_outer_clip_length);

// Complete print layout: all eight required physical clips are separate, but
// fit on a 250 x 250 mm bed (130 x 235 mm footprint including row spacing).
module print_layout() {
  print_oriented_clip(arm_clip_length, arm_clip_leg_length);
  translate([0, 30, 0]) print_oriented_clip(arm_clip_length, arm_clip_leg_length);
  translate([0, 60, 0]) print_oriented_clip(arm_clip_length, arm_clip_leg_length);
  translate([0, 90, 0]) print_oriented_clip(arm_clip_length, arm_clip_leg_length);
  translate([0, 120, 0]) print_oriented_clip(finish_outer_clip_length, finish_outer_clip_length);
  translate([0, 150, 0]) print_oriented_clip(finish_outer_clip_length, finish_outer_clip_length);
  translate([0, 180, 0]) print_oriented_clip(start_outer_clip_length, start_outer_clip_length);
  translate([0, 210, 0]) print_oriented_clip(start_outer_clip_length, start_outer_clip_length);
}

if (piece == "all") print_layout();
if (piece == "print_layout") print_layout();
if (piece == "finish_lower_arm") finish_lower_arm();
if (piece == "finish_upper_arm") finish_upper_arm();
if (piece == "finish_outer_lower") finish_outer_lower();
if (piece == "finish_outer_upper") finish_outer_upper();
if (piece == "start_lower_arm") start_lower_arm();
if (piece == "start_upper_arm") start_upper_arm();
if (piece == "start_outer_lower") start_outer_lower();
if (piece == "start_outer_upper") start_outer_upper();
