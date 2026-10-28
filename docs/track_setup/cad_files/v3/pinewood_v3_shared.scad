/* Pinewood U v3 - shared geometry for the U-frame, start foot, clips and
   electronics housing. Millimetres. Keep this file next to all entry files.
   Housing taken over unchanged from v2; only the docking (keyholes with
   less play plus wedge ramps) is new.
   Prototype fits: PETG, 0.4 mm nozzle, 0.2 mm layers.
*/
$fn=48;
// piece="print_layout"; // base | lid | print_layout | preview | exploded
show_board=true;     // only a transparent preview, never in the STL

box_width=110;
box_height=90;
box_depth=54;
wall=3;
cable_width=15;
cable_height=40;

// Documented PCB outer dimensions, not the display diagonal.
board_width=31.5;
board_height=63.7;
pcb_thickness=1.6;         // to be measured
display_rise=3;           // LCD front above PCB front: to be measured
display_gap=0.6;          // gap between LCD and lid inner face
board_fit=0.3;            // lateral insertion play per side
keeper_gap=0.3;           // test print: some play at the carrier corner supports

// Carrier per Amazon listing: 63 x 63 x 10 mm; PCB 1.6 mm.
carrier_width=63;
carrier_height=63;
carrier_pcb_thickness=1.6;
// Test print: assumed 18 mm from carrier underside to glass.
// Cannot be derived from the 10 mm carrier height alone: measure on the build!
assembly_height=18;
// ESP PCB centre relative to carrier centre: centred as a starting value.
esp_on_carrier_x=0;
esp_on_carrier_z=0;
carrier_fit=0.35;
support_foot=8;
support_top=5;
support_contact=2.5;
retainer_contact=2.5;

// Conservative viewing window; no claimed active LCD dimensions.
// Enlarge if needed; the glass stays behind the lid.
display_width=24;
display_height=44;
// Small centring rim on the lid inner face. The clear inner dimensions
// come from the old 27.8 x 50.8 mm cut-out; check against the glass.
display_guide_width=27.8;
display_guide_height=50.8;
display_guide_wall=1.2;
display_guide_depth=1.5;
display_guide_leadin=0.3; // lead-in wider than the seat on each side
// Window centre offset from PCB centre, USB at the bottom.
// Old file: display top edge 6.3 mm below PCB top edge.
// 50.8 mm is only the previous display envelope height here.
display_offset_x=0;
display_offset_z=board_height/2-6.3-50.8/2;

lid_thickness=3;
lid_fit=0.3;
lip_depth=1.8;
lip_wall=1.4;
latch_length=15;
latch_thickness=1.2;
latch_width=10;
latch_hook=1.3;
latch_fit=0.3;
latch_shoulder_y=box_depth-latch_length+4;

window_x=box_width/2;
window_z=box_height/2;
board_x=window_x-board_width/2-display_offset_x;
board_z=window_z-board_height/2-display_offset_z;
pcb_front_y=box_depth-display_gap-display_rise;
pcb_back_y=pcb_front_y-pcb_thickness;
carrier_x=board_x+board_width/2-carrier_width/2-esp_on_carrier_x;
carrier_z=board_z+board_height/2-carrier_height/2-esp_on_carrier_z;
carrier_back_y=box_depth-display_gap-assembly_height;
carrier_front_y=carrier_back_y+carrier_pcb_thickness;

assert(board_z>wall+1 && board_z+board_height<box_height-wall-1,
       "PCB does not fit vertically: adjust display offset / housing height");
assert(board_x>wall+5 && board_x+board_width<box_width-wall-5,
       "PCB does not fit horizontally");
assert(display_width<board_width && display_height<board_height,
       "window must not exceed the PCB");
assert(display_guide_width>display_width && display_guide_height>display_height
       && display_guide_depth>display_gap && display_guide_depth<display_rise+display_gap,
       "Display-Zentrierrand ungueltig");
assert(carrier_back_y>wall+4 && assembly_height>display_rise+pcb_thickness+carrier_pcb_thickness,
       "invalid stack height");
assert(carrier_x-support_foot/2>wall && carrier_x+carrier_width+support_foot/2<box_width-wall,
       "carrier does not fit horizontally");
assert(carrier_z-support_foot/2>wall && carrier_z+carrier_height+support_foot/2<box_height-wall,
       "carrier does not fit vertically");
assert(abs(esp_on_carrier_x)+board_width/2<carrier_width/2,
       "ESP seitlich ausserhalb Carrier");
assert(latch_length>10 && latch_thickness>0 && latch_hook>latch_fit,
       "Rastgeometrie ungueltig");

// Four small conical corner supports. In print orientation each post grows
// straight out of the back wall and narrows upwards, without overhang.
module carrier_supports() {
  for(sx=[0,1]) for(sz=[0,1]) {
    tx=sx==0 ? carrier_x-support_top+support_contact : carrier_x+carrier_width-support_contact;
    tz=sz==0 ? carrier_z-support_top+support_contact : carrier_z+carrier_height-support_contact;
    hull() {
      translate([tx-(support_foot-support_top)/2,wall-0.05,tz-(support_foot-support_top)/2+(sz==0 ? 14 : -14)])
        cube([support_foot,0.1,support_foot]);
      translate([tx,carrier_back_y-0.1,tz]) cube([support_top,0.1,support_top]);
    }
    // Short L-guide outside the carrier edge, only 2 mm higher
    // than the support. Does not touch the terminal strips at the sides.
    gx=sx==0 ? carrier_x-carrier_fit-1.2 : carrier_x+carrier_width+carrier_fit;
    gz=sz==0 ? carrier_z-carrier_fit-1.2 : carrier_z+carrier_height+carrier_fit;
    translate([gx,carrier_back_y-0.02,tz]) cube([1.2,2.02,support_top]);
    translate([tx,carrier_back_y-0.02,gz]) cube([support_top,2.02,1.2]);
  }
}

// Counter-holders only touch free carrier corners. The ESP and the
// screw terminals sit in between; the glass has display_gap clearance.
module lid_carrier_retainers() {
  for(x=[carrier_x,carrier_x+carrier_width-retainer_contact])
    for(z=[carrier_z,carrier_z+carrier_height-retainer_contact])
      hull() {
        translate([x,carrier_front_y+keeper_gap,z])
          cube([retainer_contact,0.1,retainer_contact]);
        translate([x-0.8,box_depth-0.05,z-0.8])
          cube([retainer_contact+1.6,0.1,retainer_contact+1.6]);
      }
}

module housing_base() {
  difference() {
    union() {
      difference() {
        cube([box_width,box_depth,box_height]);
        translate([wall,wall,wall])
          cube([box_width-2*wall,box_depth-wall+0.1,box_height-2*wall]);
      }
      carrier_supports();
      dock_ramps();
      // Low open cable lanes, below the carrier; no tie slots or closed tunnels.
      for(x=[12,box_width-13.6])
        translate([x,wall-0.05,box_height/2-15]) cube([1.6,7.05,30]);
    }
    // Shared 15 x 40 mm cable entry towards the U-frame.
    translate([(box_width-cable_width)/2,-0.1,(box_height-cable_height)/2])
      cube([cable_width,wall+0.2,cable_height]);
    docking_keyholes();
    // The USB cable can be inserted from the open front.
    translate([board_x+board_width/2-8,pcb_back_y-8,-0.1])
      cube([16,box_depth-pcb_back_y+8.1,wall+0.2]);
    // Continuous latch/release windows on both sides.
    // Closed wall above the shoulder prevents lifting off.
    for(x=[-0.1,box_width-wall-0.1])
      translate([x,0,box_height/2]) rotate([0,90,0])
        linear_extrude(height=wall+0.2)
          polygon([[-6,box_depth-latch_length-0.4],[6,box_depth-latch_length-0.4],
                   [6,latch_shoulder_y+0.45],[0,latch_shoulder_y+6.45],
                   [-6,latch_shoulder_y+0.45]]);
  }
}

module locating_lip() {
  // Centring strips with clearance for USB and side clips.
  for(z=[wall+lid_fit,box_height-wall-lid_fit-lip_wall])
    for(x=[wall+5,box_width/2+10])
      translate([x,box_depth-lip_depth,z])
        cube([box_width/2-wall-15,lip_depth+0.05,lip_wall]);
  for(x=[wall+lid_fit,box_width-wall-lid_fit-lip_wall])
    for(z=[wall+5,box_height/2+latch_width/2+3])
      translate([x,box_depth-lip_depth,z])
        cube([lip_wall,lip_depth+0.05,box_height/2-latch_width/2-wall-8]);
}

module left_latch() {
  gx=wall+latch_fit;
  gz=box_height/2-latch_width/2;
  // Spring tongue with reinforced root on the lid.
  translate([gx,box_depth-latch_length,gz])
    cube([latch_thickness,latch_length+0.05,latch_width]);
  hull() {
    translate([gx,box_depth-3,gz]) cube([latch_thickness,0.1,latch_width]);
    translate([gx,box_depth-0.1,gz]) cube([latch_thickness+1.5,0.2,latch_width]);
  }
  // Ramps on both sides: no abrupt overhang when printing.
  hull() {
    translate([gx-0.05,latch_shoulder_y-2.8,gz]) cube([0.1,0.1,latch_width]);
    translate([gx-latch_hook,latch_shoulder_y-1,gz]) cube([latch_hook+0.05,1,latch_width]);
    translate([gx-0.05,latch_shoulder_y+1.35,gz]) cube([0.1,0.1,latch_width]);
  }
}

module lid_latches() {
  left_latch();
  translate([box_width,0,0]) mirror([1,0,0]) left_latch();
}

module display_alignment_border() {
  // Seat around the display envelope, no pressure on the glass front.
  // Flat contour grown on the lid: printable without supports.
  difference() {
    translate([window_x-display_guide_width/2-display_guide_wall,
               box_depth-display_guide_depth,
               window_z-display_guide_height/2-display_guide_wall])
      cube([display_guide_width+2*display_guide_wall,
            display_guide_depth+0.05,
            display_guide_height+2*display_guide_wall]);
    hull() {
      translate([window_x-display_guide_width/2,box_depth-0.1,
                 window_z-display_guide_height/2])
        cube([display_guide_width,0.2,display_guide_height]);
      translate([window_x-display_guide_width/2-display_guide_leadin,
                 box_depth-display_guide_depth-0.1,
                 window_z-display_guide_height/2-display_guide_leadin])
        cube([display_guide_width+2*display_guide_leadin,0.2,
              display_guide_height+2*display_guide_leadin]);
    }
  }
}

module housing_lid() {
  difference() {
    union() {
      translate([0,box_depth,0]) cube([box_width,lid_thickness,box_height]);
      locating_lip();
      lid_latches();
      lid_carrier_retainers();
      display_alignment_border();
    }
    // Recessed racing stripes; 0.6 mm recess, no separate colour parts.
    for(i=[0:7]) for(j=[0:1]) if((i+j)%2==0)
      translate([window_x-12+i*3,box_depth+lid_thickness-0.5,box_height-12+j*3])
        cube([2.8,0.6,2.8]);
    for(x=[12,18,box_width-21,box_width-15])
      translate([x,box_depth+lid_thickness-0.6,10]) cube([3,0.7,box_height-20]);
    translate([window_x-display_width/2,box_depth-0.1,window_z-display_height/2])
      cube([display_width,lid_thickness+0.2,display_height]);
  }
}

module board_preview() {
  color([0.1,0.65,0.25,0.5])
    translate([carrier_x,carrier_back_y,carrier_z])
      cube([carrier_width,carrier_pcb_thickness,carrier_height]);
  // Connector envelopes for orientation only, not manufacturer CAD.
  for(x=[carrier_x+3,carrier_x+carrier_width-10])
    color([0.15,0.5,0.2,0.45])
      translate([x,carrier_front_y,carrier_z+8]) cube([7,8.4,carrier_height-16]);
  color([0.1,0.5,0.2,0.45])
    translate([board_x,pcb_back_y,board_z]) cube([board_width,pcb_thickness,board_height]);
  color([0.1,0.4,0.9,0.5])
    translate([window_x-display_width/2,pcb_front_y,window_z-display_height/2])
      cube([display_width,display_rise,display_height]);
}

module base_print() {
  translate([0,box_height,0]) rotate([90,0,0]) housing_base();
}
module lid_print() {
  translate([0,0,box_depth+lid_thickness]) rotate([-90,0,0]) housing_lid();
}
module print_layout() {
  // Back / lid outer face on the bed; min(Z)=0.
  base_print();
  translate([box_width+12,0,0]) lid_print();
}



// =====================================================================
// -------- v3: U-frame, docking, start foot, bolt passage --------
// Coordinates as in v2: +X = arm direction / U opening, X=20 = track-side
// spine face, Y = profile width (shell split plane at split_y),
// Z = height. Sensor reference faces: lower arm Z=20, upper arm Z=h-20.
// =====================================================================

// --- Sensor (Adafruit 2167), dimensions from v1, press fit proven there ---
profile=20; arm=105;
sensor_l=19.8; sensor_w=9.9; sensor_h=7.9;
sensor_x0=78.2;            // pocket 78.2..98, arm tip 98..105 solid
sensor_lens_depth=6;       // material between lens and U inner face
optic_d=7.7; optic_slot_len=2*optic_d;
optic_x=97;
sensor_back_wall=0;        // no wall between sensor and cable channel: open at the back like v1
sensor_seat_depth=1;       // lens side sits 1 mm deep in a snug seat (press fit)
sensor_side_gap=1;         // behind it 1 mm clearance per side, the sensor is held only by the seat
sensor_ledge=2;            // two support ledges behind the sensor (long sides): it cannot slip out
sensor_crush=0.25;         // crush ribs on the support ledges
sensor_front_relief=0.15;  // seat in the front shell larger per side: the back shell sets the position,
                           // the front shell does not tilt the sensor when the shells are joined
sensor_pry_w=4;            // pry notch at the arm tip: lever the sensor out with a small screwdriver
pin_fit=0.2;               // clearance of the alignment pins (first v3 print: 0.3)

// --- Profile ---
frame_width=31;            // v2: 23 -> 8 mm outer wall per shell instead of 4 mm
frame_back=-12;            // v2: -8 -> spine 32 mm deep
split_y=10;
frame_y0=split_y-frame_width/2;
frame_y1=split_y+frame_width/2;
cable_y0=split_y-cable_width/2;
cavity_x0=-5; cavity_x1=10; // cable channel in the spine, front rib 10..20 stays solid

// --- Thick U bottom ---
foot_bottom=-20;           // lower arm Z -20..20 = 40 mm (v2: 22 mm)
low_cav_z0=-8; low_cav_z1=12;   // cable channel in the lower arm (20 mm high)
up_cav_h=12;               // cable channel in the upper arm

// --- Joint between segments ---
fit=0.3;                   // dovetail clearance
dt_center=4;               // centred in the 32 mm spine
dt_neck=12; dt_head=20; dt_len=14;
// Staggered splits: back and front shell never split at the same height.
function joints(h,side)=
  h>300 ? (side==0 ? [80,h-80] : [125,h-125])
        : (side==0 ? [70] : [170]);

// --- Bolt passage (printed, long bolt + nut) ---
bolt_d=5.5;          // bore: 5.5 for M5, 4.5 for M4, 6.5 for M6
bolt_boss_d=12;      // solid tube around the bore, bridges the cable channel
nut_af=8.2;          // nut across flats + clearance (M5: 8; M4: 7.2; M6: 10.2)
nut_depth=4.2;       // nut recessed in the front shell, cannot turn
bolt_pos=[[35,-3],[70,-3]];   // [X,Z] in the lower arm, across the cable channel

// --- Clips (as in v2) ---
clip_clearance=0.25;
clip_engagement=0.6;
function spine_clip_z(h)= h>300 ? [100,h-55] : [45,h-45]; // start: above the foot gussets

// --- Housing docking: less play ---
stud_w=23;                 // stud width as in v2, independent of frame_width
mount_shift=stud_w+4;
dock_fit=0.1;              // stud in slot: +-0.1 (v2: +-0.3)
mount_entry_w=stud_w+0.4;
mount_entry_h=12.4;
mount_slot_h=6+2*dock_fit;
mount_z=32;
dock_gap=0.35;             // head <-> housing wall, nominal
dock_ramp_h=0.45;          // wedge ramp: 0.1 mm preload in the end position
dock_ramp_len=8;
housing_x=frame_back-3.5;  // glue / back face of the housing
collar_gap=0.1;

// --- Start foot (start variant only) ---
foot_clear=0.3;
foot_wall=4;
foot_flange=25;            // per side beyond the side wall
foot_gusset_h=60;          // gussets on the spine; 0 = none
angle_w=20;                // width of the metal angles (slot in the flange, +1 mm clearance)
// Optional wood screws for support only (primary: angles + bolts).
// Vertical from below into the track underside, head recessed. false = no holes.
foot_wood_screws=true;
foot_screw_x=[52.5,93];    // between and behind the angle slots
foot_screw_d=4.5; foot_screw_head_d=9; foot_screw_keep=12;

// --------------------------------------------------------------------
module docking_keyholes() {
  for(z=[window_z-mount_z,window_z+mount_z]) {
    translate([window_x-mount_shift-mount_entry_w/2,-0.1,z-mount_entry_h/2])
      cube([mount_entry_w,wall+0.2,mount_entry_h]);
    translate([window_x-mount_shift,-0.1,z-mount_slot_h/2])
      cube([mount_shift+mount_entry_w/2,wall+0.2,mount_slot_h]);
  }
}
// Wedge ramps on the inner wall on both sides of the slot. The stud head
// rides up while sliding in and sits with 0.1 mm preload in the end position.
// In print orientation they grow flat out of the back wall: no supports.
module dock_ramps() {
  xa=window_x-mount_shift+mount_entry_w/2+0.2;
  xe=window_x+mount_entry_w/2;
  for(z=[window_z-mount_z,window_z+mount_z]) for(s=[-1,1])
    translate([0,0,s>0 ? z+mount_slot_h/2 : z-6.4])
      hull() {
        translate([xa,wall-0.05,0]) cube([0.1,0.05,6.4-mount_slot_h/2]);
        translate([xa+dock_ramp_len,wall-0.05,0]) cube([xe-xa-dock_ramp_len,dock_ramp_h+0.05,6.4-mount_slot_h/2]);
      }
}
// Replaceable lock plugs against sliding back (as in v2).
module dock_lock() {
  w=mount_entry_w-0.6;
  union() {
    translate([-w/2-1.2,4.5,-7.5]) cube([w+2.4,2,15]);
    for(s=[-1,1]) scale([s,1,1]) {
      translate([w/2+0.35,3,-7.5]) cube([0.85,1.6,15]);
      translate([w/2-1.2,-1.4,-5.7]) cube([1.2,6.0,11.4]);
      hull() {
        translate([w/2-0.1,-1.4,-5.7]) cube([0.1,0.1,11.4]);
        translate([w/2-0.1,-0.7,-5.7]) cube([0.65,0.2,11.4]);
        translate([w/2-0.1,0.1,-5.7]) cube([0.1,0.1,11.4]);
      }
    }
  }
}
module lock_print() { translate([mount_entry_w/2+2,7.5,6.5]) rotate([-90,0,0]) dock_lock(); }

head_in=housing_x-wall-dock_gap;   // inner face of the stud head
module dock_studs(h) {
  for(z=[h/2-mount_z,h/2+mount_z]) {
    translate([head_in-0.05,split_y-stud_w/2,z-3]) cube([frame_back+0.1-head_in+0.05,stud_w,6]);
    translate([head_in-3.1,split_y-stud_w/2,z-6]) cube([3.1,stud_w,12]);
  }
}
module housing_on_frame(h) {
  translate([housing_x,split_y-box_width/2,h/2-box_height/2])
    rotate([0,0,90]) children();
}

// --------------------------------------------------------------------
module u_blank(h) {
  union() {
    translate([frame_back,frame_y0,foot_bottom]) cube([20-frame_back,frame_width,h+2-foot_bottom]);
    translate([0,frame_y0,foot_bottom]) cube([arm,frame_width,20-foot_bottom]);
    translate([0,frame_y0,h-20]) cube([arm,frame_width,22]);
    dock_studs(h);
    // Collar around the cable passage, rests against the housing with 0.1 mm gap.
    translate([housing_x+collar_gap,frame_y0,h/2-cable_height/2-3])
      cube([frame_back+0.1-housing_x-collar_gap,frame_width,cable_height+6]);
  }
}

// Sensor mount for the LOWER arm (upper arm: mirrored at Z=h/2).
// As in v1: the lens side of the sensor is pressed into a snug, 1 mm deep
// seat and clamps there. Behind it is open for the cables; only two
// narrow ledges under the long edges keep the sensor in the seat.
module sensor_cuts_lower() {
  lens_z=20-sensor_lens_depth;
  back_z=lens_z-sensor_h;
  // snug seat
  translate([sensor_x0,split_y-sensor_w/2,lens_z-sensor_seat_depth])
    cube([sensor_l,sensor_w,sensor_seat_depth+0.01]);
  // sensor body with side clearance, open towards the spine into the cable channel
  translate([sensor_x0-0.01,split_y-sensor_w/2-sensor_side_gap,back_z])
    cube([sensor_l+0.01,sensor_w+2*sensor_side_gap,sensor_h-sensor_seat_depth+0.01]);
  // cable space behind the sensor between the two support ledges
  translate([sensor_x0-0.01,split_y-sensor_w/2+sensor_ledge,low_cav_z0])
    cube([sensor_l-3,sensor_w-2*sensor_ledge,back_z-low_cav_z0+0.01]);
  hull() for(x=[optic_x,optic_x-(optic_slot_len-optic_d)])
    translate([x,split_y,lens_z-0.1]) cylinder(d=optic_d,h=sensor_lens_depth+0.2);
}
// Front shell only: seat slightly larger (in X and outwards in Y).
module sensor_seat_relief_lower() {
  lens_z=20-sensor_lens_depth; r=sensor_front_relief;
  translate([sensor_x0-r,split_y,lens_z-sensor_seat_depth])
    cube([sensor_l+2*r,sensor_w/2+r,sensor_seat_depth+0.01]);
}
// Both shells: notch at the end face towards the arm tip, open to the split,
// 0.5 mm below the seat floor. A screwdriver reaches under the sensor edge
// there without touching the lens.
module sensor_pry_notch_lower() {
  lens_z=20-sensor_lens_depth;
  translate([sensor_x0+sensor_l-0.01,split_y-sensor_pry_w/2,lens_z-sensor_h])
    cube([1.5,sensor_pry_w,sensor_h+0.5]);
}
// Crush ribs on both support ledges: press the sensor into the seat.
// They run across the ledges in the pressing direction (Y).
module sensor_crush_ribs_lower() {
  lens_z=20-sensor_lens_depth;
  for(x=[sensor_x0+3,sensor_x0+sensor_l-5]) for(y=[split_y-sensor_w/2,split_y+sensor_w/2-sensor_ledge])
    translate([x,y,lens_z-sensor_h-0.01]) cube([1,sensor_ledge,sensor_crush+0.01]);
}
module mirror_upper(h) { translate([0,0,h]) mirror([0,0,1]) children(); }

module cavities(h) {
  // spine
  translate([cavity_x0,cable_y0,low_cav_z0]) cube([cavity_x1-cavity_x0,cable_width,h-2-low_cav_z0]);
  // lower arm up to the sensor back
  translate([cavity_x0,cable_y0,low_cav_z0])
    cube([sensor_x0-sensor_back_wall-cavity_x0,cable_width,low_cav_z1-low_cav_z0]);
  // upper arm
  translate([cavity_x0,cable_y0,h-2-up_cav_h])
    cube([sensor_x0-sensor_back_wall-cavity_x0,cable_width,up_cav_h]);
  // cable passage into the housing
  translate([housing_x-1,cable_y0,h/2-cable_height/2]) cube([cavity_x0+1-housing_x+1,cable_width,cable_height]);
}

// Solid tube across the cable channel: the bolt never touches a cable,
// the cables pass above it. Each shell carries its half of the tube.
module bolt_bosses() {
  for(p=bolt_pos) translate([p[0],frame_y0,p[1]]) rotate([-90,0,0])
    cylinder(d=bolt_boss_d,h=frame_width);
}
module bolt_bores() {
  for(p=bolt_pos) {
    translate([p[0],frame_y0-0.1,p[1]]) rotate([-90,0,0])
      cylinder(d=bolt_d,h=frame_width+0.2);
    // hex recess for the nut on the outer face of the front shell
    translate([p[0],frame_y1-nut_depth,p[1]]) rotate([-90,0,0])
      rotate([0,0,30]) cylinder(d=nut_af/cos(30),h=nut_depth+0.1,$fn=6);
  }
}

module clip_notches(h) {
  for(z=spine_clip_z(h)) for(y=[frame_y0-0.1,frame_y1-0.7])
    translate([16.5,y,z-6]) cube([3.5,0.8,12]);
  for(y=[frame_y0-0.1,frame_y1-0.7]) translate([54,y,h-20]) cube([12,0.8,3.5]);
}

module u_frame(h) {
  union() {
    difference() {
      union() {
        difference() {
          u_blank(h);
          cavities(h);
          sensor_cuts_lower();
          mirror_upper(h) sensor_cuts_lower();
          clip_notches(h);
        }
        bolt_bosses();
      }
      bolt_bores();
    }
    sensor_crush_ribs_lower();
    mirror_upper(h) sensor_crush_ribs_lower();
  }
}

// Dovetail across the full spine depth; socket walls 6..10 mm thick
// (v2: 1 mm). Joined across the split plane (in Y), not along the spine.
module dovetail(z,clearance=0) {
  translate([0,frame_y1+0.1,z]) rotate([90,0,0])
    linear_extrude(height=frame_width+0.2)
      offset(delta=clearance)
        polygon([[dt_center-dt_neck/2,-0.1],[dt_center+dt_neck/2,-0.1],
                 [dt_center+dt_head/2,dt_len],[dt_center-dt_head/2,dt_len]]);
}

module half_box(side) {
  translate([-60,side==0 ? frame_y0-1 : split_y,foot_bottom-30])
    cube([220,side==0 ? split_y-frame_y0+1 : frame_y1-split_y+1,1000]);
}
function seg_lo(h,side,s)= s==0 ? foot_bottom-10 : joints(h,side)[s-1];
function seg_hi(h,side,s)= s==len(joints(h,side)) ? h+20 : joints(h,side)[s];
function seg_count(h,side)= len(joints(h,side))+1;

module align_pins(h,lo,hi,clearance=0) {
  for(p=[[15,40],[15,h/2],[15,h-40],[102,10],[102,h-10],[20,-14],[90,-14],[50,-14]])
    if(p[1]>lo+4 && p[1]<hi-4)
      translate([p[0],split_y-0.15,p[1]]) rotate([-90,0,0])
        cylinder(r1=1.6+clearance,r2=1.1+clearance,h=2.4+clearance);
}

module u_half(h,segment=0,side=0) {
  lo=seg_lo(h,side,segment); hi=seg_hi(h,side,segment);
  last=segment==seg_count(h,side)-1;
  difference() {
    union() {
      intersection() {
        u_frame(h);
        half_box(side);
        union() {
          translate([-60,-50,lo]) cube([220,100,hi-lo]);
          if(!last) dovetail(hi);
        }
      }
      if(side==0) align_pins(h,lo,hi);
    }
    if(segment>0) dovetail(lo,fit);
    if(side==1) align_pins(h,lo,hi,pin_fit);
    if(side==1) { sensor_seat_relief_lower(); mirror_upper(h) sensor_seat_relief_lower(); }
    sensor_pry_notch_lower(); mirror_upper(h) sensor_pry_notch_lower();
  }
}

// Print orientation: wide outer face on the bed, cable channel facing up.
module u_print(h,segment=0,side=0) {
  lo=seg_lo(h,side,segment);
  hi=seg_hi(h,side,segment);
  if(side==0) translate([-frame_back+5,hi+dt_len+5,-frame_y0]) rotate([90,0,0]) u_half(h,segment,side);
  else translate([-frame_back+5,-lo+5,frame_y1]) rotate([-90,0,0]) u_half(h,segment,side);
}

// --------------------------------------------------------------------
// Teardrop for horizontal bores when printing (45 deg roof, towards +Z).
module teardrop_y(d,len) {
  rotate([-90,0,0]) linear_extrude(height=len)
    union() {
      circle(d=d);
      rotate([0,0,225]) square(d/2);
    }
}

// --------------------------------------------------------------------
// Start foot: shoe under the lower arm, belongs to the U (not screwed to the
// track). Wide flanges (Y) sit flush with the U inner face Z=20 under the
// track and give lateral support; gussets run up the spine.
// Mounting as for the finish: metal angle on the track, its vertical leg
// sits in a slot of the flange directly against the foot wall; the long
// bolts go through angle, foot and U. To swap, undo only the 2 bolts;
// the angles stay on the track.
// Print: bottom on the bed, no supports.
module start_foot() {
  c=foot_clear; t=foot_wall;
  x0=frame_back-c-t; x1=arm;
  yi0=frame_y0-c; yi1=frame_y1+c;
  z0=foot_bottom-c-t;
  fy0=yi0-t-foot_flange; fy1=yi1+t+foot_flange;
  difference() {
    union() {
      translate([x0,fy0,z0]) cube([x1-x0,fy1-fy0,20-z0]);
      if(foot_gusset_h>0) for(s=[0,1]) hull() {
        translate([x0,s==0 ? fy0 : yi1,19.9]) cube([20-x0,yi0-fy0,0.1]);
        translate([x0,s==0 ? yi0-t : yi1,20+foot_gusset_h-0.1]) cube([20-x0,t,0.1]);
      }
      // back wall at the spine, up to the top of the gussets
      if(foot_gusset_h>0) translate([x0,yi0-t,19.9]) cube([t,yi1-yi0+2*t,foot_gusset_h+0.1]);
    }
    // pocket for the U bottom and the spine
    translate([frame_back-c,yi0,foot_bottom-c]) cube([arm+1-frame_back+c,yi1-yi0,20+foot_gusset_h+1-foot_bottom+c]);
    for(p=bolt_pos) {
      // passage for the long bolt
      translate([p[0],fy0-0.1,p[1]]) teardrop_y(bolt_d+0.2,fy1-fy0+0.2);
      // Angle side (back, Y-): slot over the full flange height,
      // the angle rests directly on the 4 mm foot wall and reaches up to the track.
      translate([p[0]-angle_w/2-0.5,fy0-0.1,z0-0.1]) cube([angle_w+1,foot_flange+0.1,20-z0+0.2]);
      // Nut side (Y+): counterbore for tool access; the nut itself sits
      // recessed in the U, only the bolt end comes out here.
      translate([p[0],yi1+t,p[1]]) teardrop_y(16,foot_flange+0.1);
    }
    // Optional: one 4 x 30 wood screw per hole, support only
    if(foot_wood_screws) for(y=[(fy0+yi0-t)/2,(fy1+yi1+t)/2]) for(x=foot_screw_x) {
      translate([x,y,z0-0.1]) cylinder(d=foot_screw_d,h=20.2-z0);
      translate([x,y,z0-0.1]) cylinder(d=foot_screw_head_d,h=20-z0-foot_screw_keep+0.1);
    }
  }
}
module start_foot_print() { translate([-frame_back+foot_clear+foot_wall,
  foot_flange+foot_wall+foot_clear-frame_y0,-(foot_bottom-foot_clear-foot_wall)]) start_foot(); }

// --------------------------------------------------------------------
// Clips as in v2 (width follows frame_width).
module frame_clip(depth=28) {
  w=frame_width; t=2; c=clip_clearance;
  union() {
    translate([-t-c,-w/2-c-t,0]) cube([t,w+2*c+2*t,10]);
    for(s=[-1,1]) scale([1,s,1]) {
      translate([-c,w/2+c,0]) cube([depth+c,t,10]);
      hull() {
        translate([depth-3.4,w/2-clip_engagement,0]) cube([1.1,c+clip_engagement+0.01,10]);
        translate([depth-1.0,w/2+c-0.05,0]) cube([0.3,0.1,10]);
      }
    }
  }
}
module clips_on_frame(h) {
  for(z=spine_clip_z(h)) translate([frame_back,split_y,z-5]) frame_clip(20-frame_back);
  translate([55,split_y,h+2]) rotate([0,90,0]) frame_clip(22);
}

// --------------------------------------------------------------------
module sensor_mock(h) {
  lens_z=20-sensor_lens_depth;
  color([0.15,0.15,0.15]) {
    translate([sensor_x0,split_y-sensor_w/2,lens_z-sensor_h]) cube([sensor_l,sensor_w,sensor_h]);
    mirror_upper(h) translate([sensor_x0,split_y-sensor_w/2,lens_z-sensor_h]) cube([sensor_l,sensor_w,sensor_h]);
  }
}

module system_preview(h=340,exploded=0,with_foot=false) {
  for(side=[0,1]) for(s=[0:seg_count(h,side)-1])
    color(side==0 ? (s%2==0 ? [0.18,0.22,0.28] : [0.26,0.31,0.38])
                  : (s%2==0 ? [0.42,0.48,0.55] : [0.52,0.58,0.65]))
      translate([0,side==0 ? -exploded : exploded,0]) u_half(h,s,side);
  color([1,0.45,0.08]) clips_on_frame(h);
  sensor_mock(h);
  if(with_foot) color([0.2,0.55,0.75]) translate([0,0,-exploded*2]) start_foot();
  housing_on_frame(h) {
    color([0.15,0.25,0.35]) translate([0,-exploded,0]) housing_base();
    color([0.95,0.5,0.12]) translate([0,exploded,0]) housing_lid();
  }
}
