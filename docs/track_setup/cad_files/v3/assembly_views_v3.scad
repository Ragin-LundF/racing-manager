// Pinewood U v3 - views for the assembly guide (not a print file).
// Keep pinewood_v3_shared.scad in the same folder.
include <pinewood_v3_shared.scad>
view="overview_start"; // overview_start | overview_finish | exploded_start | step_shells | step_sensor | step_close | step_clips | step_housing | step_mount_finish | step_mount_start
C_BACK=[0.33,0.40,0.50]; C_BACK2=[0.43,0.50,0.60];
C_FRONT=[0.55,0.61,0.68]; C_FRONT2=[0.66,0.71,0.77];
C_FOOT=[0.2,0.55,0.8]; C_CLIP=[1,0.45,0.08]; C_HOUSING=[0.15,0.25,0.35]; C_LID=[0.95,0.5,0.12];
C_STEEL=[0.72,0.72,0.75]; C_WOOD=[0.85,0.7,0.48,0.55]; C_SENSOR=[0.08,0.08,0.08];

module shells(h,dy0=0,dy1=0,dz=0,sides=[0,1]) {
  for(side=sides) for(s=[0:seg_count(h,side)-1])
    color(side==0 ? (s%2==0 ? C_BACK : C_BACK2) : (s%2==0 ? C_FRONT : C_FRONT2))
      translate([0,side==0 ? -dy0 : dy1,s*dz]) u_half(h,s,side);
}
module housing(h,dx=0) housing_on_frame(h) translate([0,dx,0]) {
  color(C_HOUSING) housing_base(); color(C_LID) housing_lid();
}
// Metal angle (customer part, schematic): vertical leg against the U/foot,
// horizontal leg screwed to the track underside.
module metal_angle(y_face) {
  for(p=bolt_pos) color(C_STEEL) translate([p[0]-angle_w/2,0,0]) difference() {
    union() {
      translate([0,y_face-3,-18]) cube([angle_w,3,38]);
      translate([0,y_face-30,17]) cube([angle_w,30,3]);
    }
    translate([angle_w/2,y_face-3.1,p[1]]) rotate([-90,0,0]) cylinder(d=5.5,h=3.2);
  }
}
module bolts(y0,y1) for(p=bolt_pos) color(C_STEEL) {
  translate([p[0],y0,p[1]]) rotate([-90,0,0]) cylinder(d=5,h=y1-y0);
  translate([p[0],y0-3.5,p[1]]) rotate([-90,0,0]) cylinder(d=9,h=3.5,$fn=6);
}
module track(x1=170) color(C_WOOD) translate([20,-90,20]) cube([x1-20,200,12]);
foot_y0=frame_y0-foot_clear-foot_wall;

if(view=="overview_start") { shells(340); color(C_CLIP) clips_on_frame(340); sensor_mock(340); housing(340); color(C_FOOT) start_foot(); }
else if(view=="overview_finish") { shells(240); color(C_CLIP) clips_on_frame(240); sensor_mock(240); housing(240); }
else if(view=="exploded_start") {
  shells(340,40,40,25); color(C_CLIP) translate([-30,0,0]) clips_on_frame(340);
  sensor_mock(340); housing(340,-60); color(C_FOOT) translate([0,0,-90]) start_foot();
}
else if(view=="step_shells") for(s=[0:seg_count(340,0)-1])
  color(s%2==0 ? C_BACK : C_BACK2) translate([0,-35*s,0]) u_half(340,s,0);
else if(view=="step_sensor") {
  intersection() { shells(340,0,0,0,[0]); translate([40,-50,-30]) cube([80,100,60]); }
  color(C_SENSOR) translate([0,25,0]) intersection() { sensor_mock(340); translate([0,-50,-30]) cube([200,100,60]); }
}
else if(view=="step_close") { shells(340,0,45); sensor_mock(340); }
else if(view=="step_clips") { shells(340); sensor_mock(340); color(C_CLIP) { for(z=spine_clip_z(340)) translate([-35,split_y,z-5]) translate([frame_back,0,0]) frame_clip(20-frame_back); translate([55,split_y,340+30]) rotate([0,90,0]) frame_clip(22); } }
else if(view=="step_housing") { shells(340); color(C_CLIP) clips_on_frame(340); translate([-60,0,0]) housing(340); }
else if(view=="step_mount_finish") {
  intersection() { union() { shells(240); sensor_mock(240); } translate([-60,-100,-40]) cube([200,200,110]); }
  track(); metal_angle(frame_y0-25); bolts(frame_y0-30,frame_y1+8);
}
else if(view=="step_mount_start") {
  intersection() { union() { shells(340); sensor_mock(340); } translate([-60,-100,-40]) cube([200,200,140]); }
  color(C_FOOT) start_foot();
  track(); metal_angle(foot_y0-25); bolts(foot_y0-35,frame_y1+8);
}
else assert(false,"Unknown view");
