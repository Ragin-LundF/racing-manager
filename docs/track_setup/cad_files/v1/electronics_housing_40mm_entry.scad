/* Separate electronics housing for the Pinewood U-frame.
   Y=0 is the flat glue face on the rear U-rail; +Y points outward.
   The 12 x 40 mm opening aligns with the cable entry in the mounting plate.
*/
$fn=36;
piece="print_layout"; // base | lid | print_layout | preview

// 95 mm: version for plug connectors and lateral cable space.
// Use 70 mm for silicone-wire-only wiring.
box_width=95;
box_height=82;
box_depth=48;
wall=3;

cable_width=12;
cable_height=40;
// Display is vertical with USB below it: 27 mm wide, 50 mm tall.
display_width=27.8;  // 0.4 mm clearance per side
display_height=50.8;
lid_thickness=2;
slide_clearance=0.25;
guide_depth=1;              // remaining outer lip of the C-groove
lid_fit_clearance=.2;       // extra clearance for PETG sliding fit
// Lid slides from left to right in integrated C-grooves.
lid_x=.2+lid_fit_clearance/2;
lid_width=box_width-wall-lid_x-.1-lid_fit_clearance/2;
lid_y=box_depth-lid_thickness-guide_depth;
lid_z=wall-.9+lid_fit_clearance/2;
lid_height=box_height-2*wall+1.8-lid_fit_clearance;

// Board position: 64 x 63 mm, USB on the lower edge.
board_width=64;
board_height=63;
board_stack=17.8;
board_x=(box_width-board_width)/2;
board_z=9.5;
// The board rear is high enough for terminal blocks below it. The tallest
// assembly has 0.3 mm clearance to the actual lid inner face
// (Y=box_depth-lid_thickness).
board_y=lid_y-board_stack-.3;

// The 50 x 27 mm display is 6.3 mm below the upper board edge.
display_x=board_x+(board_width-display_width)/2;
display_z=board_z+board_height-6.3-display_height;

// Six tall supports with 2 mm stepped seats: an outer rim protrudes 2 mm
// farther and a recessed 2 mm seat supports the board. The board is inserted
// centrally; the lid applies only light pressure. Six gaps remain for cables
// leading to the 12 x 40 mm pass-through.
module board_supports() {
  pad_depth=board_y-wall;
  overlap=4;       // 4 mm wide recessed board support
  seat_step=2;
  side_width=8;
  horizontal_width=30;
  side_height=24;

  // Top/bottom: raised 2 mm outer rim plus recessed support.
  for(z=[board_z-side_width,board_z+board_height]) {
    translate([board_x+(board_width-horizontal_width)/2,wall-.1,z])
      cube([horizontal_width,pad_depth+seat_step+.2,side_width]);
  }
  translate([board_x+(board_width-horizontal_width)/2,wall-.1,board_z])
    cube([horizontal_width,pad_depth+.2,overlap]);
  translate([board_x+(board_width-horizontal_width)/2,wall-.1,board_z+board_height-overlap])
    cube([horizontal_width,pad_depth+.2,overlap]);

  // Left/right: two stepped seats each, free center for cables.
  for(z=[board_z+5,board_z+board_height-side_height-5]) {
    translate([board_x-side_width,wall-.1,z])
      cube([side_width,pad_depth+seat_step+.2,side_height]);
    translate([board_x+board_width,wall-.1,z])
      cube([side_width,pad_depth+seat_step+.2,side_height]);
    translate([board_x,wall-.1,z]) cube([overlap,pad_depth+.2,side_height]);
    translate([board_x+board_width-overlap,wall-.1,z])
      cube([overlap,pad_depth+.2,side_height]);
  }
}

// True C-grooves within the 3 mm housing walls. The lid is 1 mm below the
// outer face and is guided along its top, bottom, and right edges.
module lid_channel_cuts() {
  channel_y=lid_y-.1;
  channel_h=lid_thickness+.2;
  // Lower and upper longitudinal grooves.
  translate([-.1,channel_y,lid_z-.1])
    cube([box_width-wall+.2,channel_h,1.2]);
  translate([-.1,channel_y,lid_z+lid_height-1.1])
    cube([box_width-wall+.2,channel_h,1.2]);
  // Right vertical groove; the solid right wall remains the end stop.
  translate([lid_x+lid_width-1.1,channel_y,lid_z-.1])
    cube([1.2,channel_h,lid_height+.2]);
  // Fully open the left entry: the outer lip on this side would otherwise form
  // a long, fragile bridge over the lid entry. The lid remains guided by the
  // top, bottom, and right C-grooves.
  translate([-.1,channel_y,lid_z-.1])
    cube([wall+.2,box_depth-channel_y+.2,lid_height+.2]);
}

module housing_base() {
  union() {
    difference() {
      cube([box_width,box_depth,box_height]);
      // Open outer side, closed by the lid after assembly.
      translate([wall,wall,wall])
        cube([box_width-2*wall,box_depth-wall+0.1,box_height-2*wall]);
      // Pass-through to the 12 x 40 mm cable entry in the U-frame.
      translate([(box_width-cable_width)/2,-.1,(box_height-cable_height)/2])
        cube([cable_width,wall+.2,cable_height]);
      // USB access from below, centered and open towards the outside.
      translate([box_width/2-8,box_depth-18,-.1]) cube([16,18,wall+.2]);
      lid_channel_cuts();
    }
    board_supports();
  }
}

module housing_lid() {
  difference() {
    // Completely flat rear: print directly on the bed without supports.
    translate([lid_x,lid_y,lid_z])
      cube([lid_width,lid_thickness,lid_height]);
    // Display is visible only from the outside.
    translate([display_x,lid_y-.1,display_z])
      cube([display_width,lid_thickness+2,display_height]);
    // Fingernail recess at the open left insertion side.
    translate([lid_x-.1,lid_y-.1,box_height/2])
      rotate([90,0,0]) cylinder(d=12,h=lid_thickness+2);
  }
}

module print_layout() {
  housing_base();
  translate([box_width+12,0,0]) rotate([90,0,0]) housing_lid();
}

if(piece=="base") housing_base();
if(piece=="lid") housing_lid();
if(piece=="print_layout") print_layout();
if(piece=="preview") { housing_base(); housing_lid(); }
