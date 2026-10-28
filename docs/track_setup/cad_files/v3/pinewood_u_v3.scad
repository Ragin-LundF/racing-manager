// Pinewood-U v3 – Rahmen, Start-Fuß, Hülsen, Klammern.
// pinewood_v3_shared.scad im selben Ordner lassen.
include <pinewood_v3_shared.scad>
variant="start_left"; // start_left | start_right | finish_left | finish_right
piece="preview"; // preview | exploded | half | start_foot | sleeve | spine_clip | arm_clip | fit_sensor | fit_joint
segment=0; // 0 = unten, 1.. = weiter oben (Anzahl je Schale siehe README)
side=0;    // 0 = Rückschale, 1 = Vorderschale
h=(variant=="start_left" || variant=="start_right") ? 340 : 240;
is_start=(variant=="start_left" || variant=="start_right");
right=(variant=="start_right" || variant=="finish_right");
module handed() { if(right) mirror([1,0,0]) children(); else children(); }
if(piece=="half") handed() u_print(h,segment,side);
else if(piece=="start_foot") handed() start_foot_print();
else if(piece=="sleeve") sleeve();
else if(piece=="spine_clip") translate([3,20,0]) frame_clip(20-frame_back);
else if(piece=="arm_clip") translate([3,20,0]) frame_clip(22);
// Probedruck: nur die untere Armspitze mit Sensortasche (beide Schalen).
else if(piece=="fit_sensor") {
  translate([0,0,-frame_y0]) rotate([90,0,0]) intersection() { u_half(h,0,0); translate([70,-50,-5]) cube([40,100,30]); }
  translate([0,40,frame_y1]) rotate([-90,0,0]) intersection() { u_half(h,0,1); translate([70,-50,-5]) cube([40,100,30]); }
}
// Probedruck: Schwalbenschwanz (Rückschale, erste Trennstelle).
else if(piece=="fit_joint") {
  j=joints(h,0)[0];
  translate([0,0,-frame_y0]) rotate([90,0,0]) intersection() { u_half(h,0,0); translate([-20,-50,j-25]) cube([45,100,25+dt_len+1]); }
  translate([50,0,-frame_y0]) rotate([90,0,0]) intersection() { u_half(h,1,0); translate([-20,-50,j]) cube([45,100,30]); }
}
else if(piece=="preview" || piece=="exploded")
  handed() system_preview(h,piece=="exploded" ? 25 : 0,is_start);
else assert(false,"Unknown piece");
