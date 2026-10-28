/* Break-beam sensor holder — recessed cable channel with sliding cover.
   The gray area in the sketch: 6 mm cable channel.
   The sensor pocket within it: another 4 mm (10 mm total from the outer face).
*/
$fn=56;
piece="outer_open"; // assembly | inner_view | outer_open | cover

arm=105; profile=20; gap=180;
sensor_l=19.8; sensor_w=9.9; sensor_h=7.9;
channel_depth=10; sensor_extra_depth=4; optic_d=7.7;
optic_slot_len=2*optic_d;
channel_w=16;                    // 2 mm margin on both sides of the 20 mm profile
optic_x=arm-8; optic_y=profile/2;
// Sensor pocket ends 7 mm before the arm tip; the optical channel is 8 mm before it.
// This leaves the outer tip as a solid, uninterrupted 7 mm bridge.
sensor_x0=arm-sensor_l-7;
channel_end=arm-7; // Cable channel and cover end before the solid arm end.
cover_travel=12;  // Free travel at the channel end towards the control board.
cover_length=channel_end-cover_travel;

// 6 mm cable channel from outside, plus a 4 mm deeper sensor pocket.
module outer_cuts(z,upper=true) {
  if(upper) {
    // Deep 10 mm trough in the middle. The upper lip AND lower support remain
    // on both sides; together they form the closed C-groove.
    translate([0,4,z+profile-channel_depth-.1]) cube([channel_end,12,channel_depth+.2]);
    translate([0,2,z+profile-4.15]) cube([channel_end,2,2.3]);
    translate([0,16,z+profile-4.15]) cube([channel_end,2,2.3]);
    translate([sensor_x0,(profile-sensor_w)/2,z+profile-channel_depth-sensor_extra_depth-.1])
      cube([sensor_l,sensor_w,channel_depth+sensor_extra_depth+.2]);
    // Optical path from the inner U face to the recessed sensor lens.
    hull() {
      translate([optic_x,optic_y,z-.1]) cylinder(d=optic_d,h=profile-channel_depth-sensor_extra_depth+.2);
      translate([optic_x-(optic_slot_len-optic_d),optic_y,z-.1])
        cylinder(d=optic_d,h=profile-channel_depth-sensor_extra_depth+.2);
    }
  } else {
    translate([0,4,z-.1]) cube([channel_end,12,channel_depth+.2]);
    translate([0,2,z+1.85]) cube([channel_end,2,2.3]);
    translate([0,16,z+1.85]) cube([channel_end,2,2.3]);
    translate([sensor_x0,(profile-sensor_w)/2,z-.1])
      cube([sensor_l,sensor_w,channel_depth+sensor_extra_depth+.2]);
    hull() {
      translate([optic_x,optic_y,z+channel_depth+sensor_extra_depth-.1])
        cylinder(d=optic_d,h=profile-channel_depth-sensor_extra_depth+.2);
      translate([optic_x-(optic_slot_len-optic_d),optic_y,z+channel_depth+sensor_extra_depth-.1])
        cylinder(d=optic_d,h=profile-channel_depth-sensor_extra_depth+.2);
    }
  }
}

module arm_body(z,upper=true) {
  difference() {
    translate([0,0,z]) cube([arm,profile,profile]);
    outer_cuts(z,upper);
  }
}

// 2 mm cover in the C-profile: 2 mm below the outer face, with side edges
// beneath the lips. It is 12 mm shorter than the channel, allowing it to slide.
module slider_cover(z,upper=true,xpos=0) {
  if(upper) {
    // 0.15 mm clearance to the upper and lower C-groove guides.
    translate([xpos,2.15,z+profile-4.0]) cube([cover_length,15.7,2]);
  } else {
    translate([xpos,2.15,z+2.0]) cube([cover_length,15.7,2]);
  }
}

// Visual check only — the actual sensor half is concealed by the cover.
module sensor_mock(z,upper=true) {
  if(upper)
    translate([sensor_x0,(profile-sensor_w)/2,z+profile-channel_depth-sensor_extra_depth])
      cube([sensor_l,sensor_w,sensor_h]);
  else
    translate([sensor_x0,(profile-sensor_w)/2,z+channel_depth+sensor_extra_depth-sensor_h])
      cube([sensor_l,sensor_w,sensor_h]);
}

module complete() {
  arm_body(0,false);
  arm_body(gap+profile,true);
  // Closed position: cover pushed to sensor pocket, 12 mm travel remains free at the rear.
  slider_cover(0,false,cover_travel);
  slider_cover(gap+profile,true,cover_travel);
}

if(piece=="assembly") complete();
if(piece=="inner_view") { arm_body(0,false); arm_body(gap+profile,true); }
if(piece=="outer_open") { arm_body(0,false); arm_body(gap+profile,true); color("dimgray") sensor_mock(0,false); color("dimgray") sensor_mock(gap+profile,true); }
if(piece=="cover") { slider_cover(0,false); translate([0,22,0]) slider_cover(0,true); }
