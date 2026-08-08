/* Pinewood Derby U-frame with a continuous concealed cable channel — no electronics housing.
   +X = U opening / track side, X=0 = rear face of the tall rail. The separate
   housing is glued over the 40 mm cable entry at the center of the rear face.
*/
$fn=48;
variant="start_left"; // start_left | start_right | finish_left | finish_right
piece="preview";      // preview | frame | arm_covers | vertical_covers | lower_arm_cover | upper_arm_cover | lower_vertical_cover | upper_vertical_cover | print_layout | start_bottom | start_middle | start_top

profile=20; arm=105; channel_depth=10; sensor_extra=4;
channel_end=98;       // 7 mm solid arm tip
sensor_l=19.8; sensor_w=9.9; sensor_h=7.9; optic_d=7.7;
optic_slot_len=2*optic_d;  // internal opening: 15.4 x 7.7 mm
sensor_x0=78.2; optic_x=97; optic_y=10;
// Arm covers meet the rear vertical channel with a 2 mm gap.
arm_cover_start=2; arm_cover_len=channel_end-arm_cover_start;
mid_gap=40;
mount_width=60;      // Glue area across the cable channel
mount_height=80;     // 20 mm margin above/below the 40 mm cable entry
joint_z=80;          // Height of the two start-frame corner modules
joint_depth=20;      // Length of the keyed joining tongues
function H()=(variant=="start_left" || variant=="start_right") ? 340 : 240;
function mirrored()=(variant=="start_right" || variant=="finish_right");

module raw_u(h) {
  union() {
    cube([profile,profile,h]);
    cube([arm,profile,profile]);
    translate([0,0,h-profile]) cube([arm,profile,profile]);
  }
}

// C-channel on the outer face of either transverse arm.
module arm_channel_cuts(z,upper=true) {
  if(upper) {
    // Central 12 mm trough, 10 mm deep; a closed C-groove on each side.
    translate([0,4,z+profile-channel_depth-.1]) cube([channel_end,12,channel_depth+.2]);
    translate([0,2,z+profile-4.15]) cube([channel_end,2,2.3]);
    translate([0,16,z+profile-4.15]) cube([channel_end,2,2.3]);
    // Additional 4 mm-deep sensor pocket.
    translate([sensor_x0,(profile-sensor_w)/2,z+profile-channel_depth-sensor_extra-.1])
      cube([sensor_l,sensor_w,channel_depth+sensor_extra+.2]);
    // Internal opening as a slot. Its front edge remains at the original circle
    // position; the extension only goes towards the cable / rail side.
    hull() {
      translate([optic_x,optic_y,z-.1]) cylinder(d=optic_d,h=profile-channel_depth-sensor_extra+.2);
      translate([optic_x-(optic_slot_len-optic_d),optic_y,z-.1])
        cylinder(d=optic_d,h=profile-channel_depth-sensor_extra+.2);
    }
  } else {
    translate([0,4,z-.1]) cube([channel_end,12,channel_depth+.2]);
    translate([0,2,z+1.85]) cube([channel_end,2,2.3]);
    translate([0,16,z+1.85]) cube([channel_end,2,2.3]);
    translate([sensor_x0,(profile-sensor_w)/2,z-.1])
      cube([sensor_l,sensor_w,channel_depth+sensor_extra+.2]);
    hull() {
      translate([optic_x,optic_y,z+channel_depth+sensor_extra-.1])
        cylinder(d=optic_d,h=profile-channel_depth-sensor_extra+.2);
      translate([optic_x-(optic_slot_len-optic_d),optic_y,z+channel_depth+sensor_extra-.1])
        cylinder(d=optic_d,h=profile-channel_depth-sensor_extra+.2);
    }
  }
}

// C-channel on the rear face of the tall rail (X=0), opposite the U opening.
// The side faces and the inner U face remain fully closed.
module vertical_channel_cut(h) {
  // Deep 12 mm trough, 10 mm into the rail from X=0.
  translate([-.1,4,0]) cube([channel_depth+.2,12,h]);
  // C-guides end at the 40 mm cable entry. There is only an open trough there,
  // no cover rail, so the covers stop automatically.
  guide_len=h/2-mid_gap/2;
  for (guide_z=[0,h/2+mid_gap/2]) {
    translate([1.85,2,guide_z]) cube([2.3,2,guide_len]);
    translate([1.85,16,guide_z]) cube([2.3,2,guide_len]);
  }
}

// Laterally widened rail: 60 mm across, around the cable channel, and through
// the entire 20 mm profile depth. This keeps the rear face flat for gluing.
module mounting_plate(h) {
  translate([0,-(mount_width-profile)/2,h/2-mount_height/2])
    cube([profile,mount_width,mount_height]);
}

// This cut is applied only after uniting the U-frame and mounting plate. It
// continues the rear 10 mm cable trough across the complete 80 mm mounting
// height. The remaining 10 mm towards the inner U face intentionally stays closed.
module housing_cable_transition(h) {
  translate([-.2,3.8,h/2-mount_height/2])
    cube([channel_depth+.4,12.4,mount_height]);
}

module frame(h) {
  difference() {
    union() {
      difference() {
        raw_u(h);
        arm_channel_cuts(0,false);
        arm_channel_cuts(h-profile,true);
        vertical_channel_cut(h);
      }
      mounting_plate(h);
    }
    housing_cable_transition(h);
  }
}

// The asymmetric tongue is on the solid inner side of the rail. It cannot
// collide with the rear cable channel and enforces the correct orientation.
module joint_tongue(z0) {
  translate([10,3,z0]) cube([10,14,joint_depth]);
}
module joint_socket(z0) {
  translate([9.7,2.7,z0]) cube([10.6,14.6,joint_depth+.2]);
}

// Start frame for 250 mm printers: 80 mm corner modules and a 180 mm middle rail.
module start_bottom() {
  union() {
    intersection() { frame(340); translate([-5,-21,-1]) cube([111,62,joint_z+1]); }
    joint_tongue(joint_z);
  }
}
module start_top() {
  union() {
    intersection() { frame(340); translate([-5,-21,340-joint_z]) cube([111,62,joint_z+1]); }
    joint_tongue(340-joint_z-joint_depth);
  }
}
module start_middle() {
  difference() {
    intersection() { frame(340); translate([-5,-21,joint_z]) cube([111,62,340-2*joint_z]); }
    joint_socket(joint_z);
    joint_socket(340-joint_z-joint_depth);
  }
}

// Arm cover: 2 mm thick, 2 mm below the outer face, guided in the C-grooves.
module arm_cover(z,upper=true,xpos=arm_cover_start) {
  if(upper)
    translate([xpos,2.15,z+profile-4]) cube([arm_cover_len,15.7,2]);
  else
    translate([xpos,2.15,z+2]) cube([arm_cover_len,15.7,2]);
}

// Rear cover for the tall rail. The 40 mm center is intentionally left open.
module vertical_cover(z0,len) {
  translate([2,2.15,z0]) cube([2,15.7,len]);
}

module all_covers(h) {
  arm_cover(0,false);
  arm_cover(h-profile,true);
  lower_len=h/2-mid_gap/2;
  upper_z=h/2+mid_gap/2;
  vertical_cover(0,lower_len);
  vertical_cover(upper_z,h-upper_z);
}

// All four covers arranged separately and flat — directly printable without supports.
module cover_print_layout(h) {
  low_len=h/2-mid_gap/2;
  translate([0,0,0]) cube([arm_cover_len,15.7,2]);
  translate([0,20,0]) cube([arm_cover_len,15.7,2]);
  translate([0,40,0]) cube([low_len,15.7,2]);
  translate([0,60,0]) cube([low_len,15.7,2]);
}

module selected() {
  if(mirrored()) mirror([1,0,0]) children(); else children();
}

if(piece=="frame") selected() frame(H());
if(piece=="arm_covers") selected() { arm_cover(0,false); arm_cover(H()-profile,true); }
if(piece=="vertical_covers") selected() {
  vertical_cover(0,H()/2-mid_gap/2);
  vertical_cover(H()/2+mid_gap/2,H()/2-mid_gap/2);
}
if(piece=="lower_arm_cover") arm_cover(0,false);
if(piece=="upper_arm_cover") arm_cover(H()-profile,true);
if(piece=="lower_vertical_cover") vertical_cover(0,H()/2-mid_gap/2);
if(piece=="upper_vertical_cover") vertical_cover(H()/2+mid_gap/2,H()/2-mid_gap/2);
if(piece=="print_layout") cover_print_layout(H());
if(piece=="start_bottom") selected() start_bottom();
if(piece=="start_middle") selected() start_middle();
if(piece=="start_top") selected() start_top();
if(piece=="preview") selected() { frame(H()); all_covers(H()); }
