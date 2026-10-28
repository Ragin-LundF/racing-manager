// Electronics housing v3 (as v2; docking with less play plus wedge ramps).
// Keep pinewood_v3_shared.scad in the same folder.
include <pinewood_v3_shared.scad>
piece="print_layout"; // base | lid | lock | preview | print_layout
if(piece=="base") base_print();
else if(piece=="lid") lid_print();
else if(piece=="lock") lock_print();
else if(piece=="preview") { housing_base(); housing_lid(); %board_preview(); }
else if(piece=="print_layout") { print_layout(); translate([0,box_height+8,0]) lock_print(); translate([35,box_height+8,0]) lock_print(); }
else assert(false,"Unknown piece");
