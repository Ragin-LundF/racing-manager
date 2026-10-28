// Pinewood U v3 - frame shells, start foot, clips, test prints.
// Keep pinewood_v3_shared.scad in the same folder.
include <pinewood_v3_shared.scad>
variant="start_left"; // start_left | start_right | finish_left | finish_right
piece="preview"; // preview | exploded | half | start_foot | spine_clip | arm_clip | fit_sensor | fit_joint
segment=0; // 0 = bottom, 1.. = further up (count per shell: see README)
side=0;    // 0 = back shell, 1 = front shell
h=(variant=="start_left" || variant=="start_right") ? 340 : 240;
is_start=(variant=="start_left" || variant=="start_right");
right=(variant=="start_right" || variant=="finish_right");
// Sensor seat test print: both arm tips (lower + upper), each with both shells.
// 4 small parts, printed flat like the real shells. The upper arm is rotated
// so that its outer face also lies on the bed.
module tip(upper,sd) {
  seg = upper ? seg_count(h,sd)-1 : 0;
  z0 = upper ? h-22 : -10;
  translate([-72,0,-z0]) intersection() {
    u_half(h,seg,sd);
    translate([72,-50,z0]) cube([34,100,upper ? 24 : 30]);
  }
}
module tip_print(upper,sd) {
  if(sd==0) translate([0,0,-frame_y0]) rotate([90,0,0]) tip(upper,sd);
  else translate([0,0,frame_y1]) rotate([-90,0,0]) tip(upper,sd);
}
module handed() { if(right) mirror([1,0,0]) children(); else children(); }
if(piece=="half") handed() u_print(h,segment,side);
else if(piece=="start_foot") handed() start_foot_print();
else if(piece=="spine_clip") translate([3,20,0]) frame_clip(20-frame_back);
else if(piece=="arm_clip") translate([3,20,0]) frame_clip(22);
else if(piece=="fit_sensor") {
  translate([0,35,0]) tip_print(false,0);
  translate([45,5,0]) tip_print(false,1);
  translate([0,75,0]) tip_print(true,0);
  translate([45,45,0]) tip_print(true,1);
}
// Test print: dovetail (back shell, first split).
else if(piece=="fit_joint") {
  j=joints(h,0)[0];
  translate([0,0,-frame_y0]) rotate([90,0,0]) intersection() { u_half(h,0,0); translate([-20,-50,j-25]) cube([45,100,25+dt_len+1]); }
  translate([50,0,-frame_y0]) rotate([90,0,0]) intersection() { u_half(h,1,0); translate([-20,-50,j]) cube([45,100,30]); }
}
else if(piece=="preview" || piece=="exploded")
  handed() system_preview(h,piece=="exploded" ? 25 : 0,is_start);
else assert(false,"Unknown piece");
