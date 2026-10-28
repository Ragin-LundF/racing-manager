/* Pinewood-U v3 – gemeinsame Geometrie für U-Rahmen, Start-Fuß, Hülsen und
   Elektronikgehäuse. Millimeter. Datei neben allen Einstiegsdateien lassen.
   Gehäuse unverändert aus v2 übernommen, nur die Andockung (Schlüssellöcher
   mit weniger Spiel und Keil-Rampen) ist neu.
   Prototyp-Passungen: PETG, 0,4-mm-Düse, 0,2-mm-Schichten.
*/
$fn=48;
// piece="print_layout"; // base | lid | print_layout | preview | exploded
show_board=true;     // nur transparente Vorschau, nie im STL

box_width=110;
box_height=90;
box_depth=54;
wall=3;
cable_width=15;
cable_height=40;

// Dokumentierte PCB-Aussenmasse, nicht die Displaydiagonale.
board_width=31.5;
board_height=63.7;
pcb_thickness=1.6;         // zu messen
display_rise=3;           // LCD-Vorderseite ueber PCB-Vorderseite: zu messen
display_gap=0.6;          // Abstand LCD / Deckelinnenseite
board_fit=0.3;            // seitliches Einsetzspiel pro Seite
keeper_gap=0.3;           // Probedruck: etwas Spiel an den Carrier-Eckauflagen

// Carrier laut Amazon: 63 x 63 x 10 mm; PCB 1.6 mm.
carrier_width=63;
carrier_height=63;
carrier_pcb_thickness=1.6;
// Probedruck: angenommene 18 mm ab Carrier-Unterseite bis Glas.
// Nicht aus 10 mm Carrierhoehe allein ableitbar: am Aufbau messen!
assembly_height=18;
// ESP-PCB-Mitte relativ zur Carrier-Mitte: Ausgangswert mittig.
esp_on_carrier_x=0;
esp_on_carrier_z=0;
carrier_fit=0.35;
support_foot=8;
support_top=5;
support_contact=2.5;
retainer_contact=2.5;

// Konservatives Sichtfenster; keine behaupteten aktiven LCD-Masse.
// Bei Bedarf vergroessern; Glas bleibt hinter dem Deckel.
display_width=24;
display_height=44;
// Kleiner Zentrierrand auf der Deckelinnenseite. Die freien Innenmasse
// stammen vom alten 27.8 x 50.8-mm-Ausschnitt; am Glas pruefen.
display_guide_width=27.8;
display_guide_height=50.8;
display_guide_wall=1.2;
display_guide_depth=1.5;
display_guide_leadin=0.3; // Einfahrt je Seite breiter als der Sitz
// Abstand Fensterzentrum vom PCB-Zentrum, USB unten.
// Alte Datei: Displayoberkante 6.3 mm unter PCB-Oberkante.
// 50.8 mm ist hier nur die bisherige Displayhuellenhoehe.
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
       "PCB passt vertikal nicht: Displayoffset / Gehaeusehoehe anpassen");
assert(board_x>wall+5 && board_x+board_width<box_width-wall-5,
       "PCB passt horizontal nicht");
assert(display_width<board_width && display_height<board_height,
       "Fenster darf PCB nicht ueberschreiten");
assert(display_guide_width>display_width && display_guide_height>display_height
       && display_guide_depth>display_gap && display_guide_depth<display_rise+display_gap,
       "Display-Zentrierrand ungueltig");
assert(carrier_back_y>wall+4 && assembly_height>display_rise+pcb_thickness+carrier_pcb_thickness,
       "Aufbauhoehe ungueltig");
assert(carrier_x-support_foot/2>wall && carrier_x+carrier_width+support_foot/2<box_width-wall,
       "Carrier passt horizontal nicht");
assert(carrier_z-support_foot/2>wall && carrier_z+carrier_height+support_foot/2<box_height-wall,
       "Carrier passt vertikal nicht");
assert(abs(esp_on_carrier_x)+board_width/2<carrier_width/2,
       "ESP seitlich ausserhalb Carrier");
assert(latch_length>10 && latch_thickness>0 && latch_hook>latch_fit,
       "Rastgeometrie ungueltig");

// Vier kleine, konische Eckstuetzen. In Drucklage waechst jeder Pfosten
// direkt aus der Rueckwand; nach oben wird er schmaler, ohne Ueberhang.
module carrier_supports() {
  for(sx=[0,1]) for(sz=[0,1]) {
    tx=sx==0 ? carrier_x-support_top+support_contact : carrier_x+carrier_width-support_contact;
    tz=sz==0 ? carrier_z-support_top+support_contact : carrier_z+carrier_height-support_contact;
    hull() {
      translate([tx-(support_foot-support_top)/2,wall-0.05,tz-(support_foot-support_top)/2+(sz==0 ? 14 : -14)])
        cube([support_foot,0.1,support_foot]);
      translate([tx,carrier_back_y-0.1,tz]) cube([support_top,0.1,support_top]);
    }
    // Kurze L-Fuehrung ausserhalb der Carrierkante, nur 2 mm hoeher
    // als die Auflage. Kein Eingriff in die Klemmleisten an den Seiten.
    gx=sx==0 ? carrier_x-carrier_fit-1.2 : carrier_x+carrier_width+carrier_fit;
    gz=sz==0 ? carrier_z-carrier_fit-1.2 : carrier_z+carrier_height+carrier_fit;
    translate([gx,carrier_back_y-0.02,tz]) cube([1.2,2.02,support_top]);
    translate([tx,carrier_back_y-0.02,gz]) cube([support_top,2.02,1.2]);
  }
}

// Gegenhalter beruehren nur freie Carrier-Ecken. Der ESP und die
// Schraubklemmen stehen dazwischen; das Glas hat display_gap Luft.
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
    // Gemeinsamer 15 x 40 mm Kabeleinlass zur U-Montage.
    translate([(box_width-cable_width)/2,-0.1,(box_height-cable_height)/2])
      cube([cable_width,wall+0.2,cable_height]);
    docking_keyholes();
    // USB-Kabel kann von der offenen Vorderseite eingelegt werden.
    translate([board_x+board_width/2-8,pcb_back_y-8,-0.1])
      cube([16,box_depth-pcb_back_y+8.1,wall+0.2]);
    // Durchgehende Rast-/Entriegelungsfenster an beiden Seiten.
    // Geschlossene Wand oberhalb der Schulter verhindert Abheben.
    for(x=[-0.1,box_width-wall-0.1])
      translate([x,0,box_height/2]) rotate([0,90,0])
        linear_extrude(height=wall+0.2)
          polygon([[-6,box_depth-latch_length-0.4],[6,box_depth-latch_length-0.4],
                   [6,latch_shoulder_y+0.45],[0,latch_shoulder_y+6.45],
                   [-6,latch_shoulder_y+0.45]]);
  }
}

module locating_lip() {
  // Zentrierleisten mit Freiraum fuer USB und Seitenclips.
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
  // Federzunge mit verstaerktem Ansatz am Deckel.
  translate([gx,box_depth-latch_length,gz])
    cube([latch_thickness,latch_length+0.05,latch_width]);
  hull() {
    translate([gx,box_depth-3,gz]) cube([latch_thickness,0.1,latch_width]);
    translate([gx,box_depth-0.1,gz]) cube([latch_thickness+1.5,0.2,latch_width]);
  }
  // Beidseitige Rampen: im Druck ohne unvermittelte Auskragung.
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
  // Sitz um die Displayhuelle, kein Druck auf die Glasvorderseite.
  // Flache, auf dem Deckel gewachsene Kontur: ohne Supports druckbar.
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
  // Connectorhuellen nur zur Orientierung, nicht Hersteller-CAD.
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
  // Rueckseite / Deckelaussenseite aufs Bett; min(Z)=0.
  base_print();
  translate([box_width+12,0,0]) lid_print();
}



// =====================================================================
// -------- v3: U-Rahmen, Andockung, Start-Fuß, Schraubhülsen --------
// Koordinaten wie v2: +X = Armrichtung/U-Öffnung, X=20 = bahnseitige
// Stegfläche, Y = Profilbreite (Trennfuge der Halbschalen bei split_y),
// Z = Höhe. Sensor-Bezugsflächen: unterer Arm Z=20, oberer Arm Z=h-20.
// =====================================================================

// --- Sensor (Adafruit 2167), Maße aus v1, dort erprobter Klemmsitz ---
profile=20; arm=105;
sensor_l=19.8; sensor_w=9.9; sensor_h=7.9;
sensor_x0=78.2;            // Tasche 78,2..98, Armspitze 98..105 massiv
sensor_lens_depth=6;       // Material zwischen Linse und U-Innenfläche
optic_d=7.7; optic_slot_len=2*optic_d;
optic_x=97;
sensor_back_wall=2.4;      // NEU: Rückwand hinter dem Sensor (v2: offen)
sensor_cable_slot=5;       // Kabelschlitz mittig in der Rückwand
sensor_crush=0.25;         // Quetschrippen für spielfreien Sitz

// --- Profil ---
frame_width=31;            // v2: 23 -> je Schale 8 mm Außenwand statt 4 mm
frame_back=-12;            // v2: -8 -> Steg 32 mm tief
split_y=10;
frame_y0=split_y-frame_width/2;
frame_y1=split_y+frame_width/2;
cable_y0=split_y-cable_width/2;
cavity_x0=-5; cavity_x1=10; // Kabelraum im Steg, Vorderrippe 10..20 bleibt massiv

// --- Dicker U-Boden ---
foot_bottom=-20;           // unterer Arm Z -20..20 = 40 mm (v2: 22 mm)
low_cav_z0=-8; low_cav_z1=12;   // Kabelraum im unteren Arm (20 mm hoch)
up_cav_h=12;               // Kabelraum im oberen Arm

// --- Steckverbindung zwischen Segmenten ---
fit=0.3;                   // Spiel Schwalbenschwanz
dt_center=4;               // mittig im 32-mm-Steg
dt_neck=12; dt_head=20; dt_len=14;
// Versetzte Trennstellen: Rück- und Vorderschale nie auf gleicher Höhe.
function joints(h,side)=
  h>300 ? (side==0 ? [80,h-80] : [125,h-125])
        : (side==0 ? [70] : [170]);

// --- Schraubhülsen (geführte Durchführung, M4) ---
sleeve_od=8; sleeve_id=4.5; sleeve_fit=0.3;
sleeve_collar_d=12; sleeve_collar_t=2;
sleeve_pos=[[35,-3],[70,-3]];   // [X,Z] im unteren Arm, quer durch den Kabelraum

// --- Klammern (wie v2) ---
clip_clearance=0.25;
clip_engagement=0.6;
function spine_clip_z(h)= h>300 ? [100,h-55] : [45,h-45]; // Start: oberhalb der Fuß-Stützen

// --- Andockung Gehäuse: weniger Spiel ---
stud_w=23;                 // Zapfenbreite wie v2, unabhängig von frame_width
mount_shift=stud_w+4;
dock_fit=0.1;              // Steg im Schlitz: +-0,1 (v2: +-0,3)
mount_entry_w=stud_w+0.4;
mount_entry_h=12.4;
mount_slot_h=6+2*dock_fit;
mount_z=32;
dock_gap=0.35;             // Kopf <-> Gehäusewand nominell
dock_ramp_h=0.45;          // Keilrampe: 0,1 mm Vorspannung in Endlage
dock_ramp_len=8;
housing_x=frame_back-3.5;  // Klebe-/Rückseite des Gehäuses
collar_gap=0.1;

// --- Start-Fuß (nur Start-Variante) ---
foot_clear=0.3;
foot_wall=4;
foot_flange=25;            // je Seite über die Seitenwand hinaus
foot_gusset_h=60;          // Stützdreiecke am Steg; 0 = ohne
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
// Keilrampen auf der Wandinnenseite beidseits des Schlitzes. Der Zapfenkopf
// läuft beim Einschieben auf und sitzt in Endlage mit 0,1 mm Vorspannung.
// In Drucklage wachsen sie flach aus der Rückwand: supportfrei.
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
// Ersetzbare Sperrstopfen gegen Zurückschieben (wie v2).
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

head_in=housing_x-wall-dock_gap;   // Innenfläche Zapfenkopf
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
    // Kragen um den Kabeldurchgang, liegt mit 0,1 mm Luft am Gehäuse an.
    translate([housing_x+collar_gap,frame_y0,h/2-cable_height/2-3])
      cube([frame_back+0.1-housing_x-collar_gap,frame_width,cable_height+6]);
  }
}

// Sensortasche, Lichtschlitz und Kabelschlitz für den UNTEREN Arm. Der obere
// Arm nutzt dieselbe Geometrie, gespiegelt an Z=h/2.
// Die Tasche ist rundum geschlossen (Boden, Rückwand, Seiten): der Sensor
// wird in die Halbschale gelegt und beim Schließen eingeklemmt.
module sensor_cuts_lower() {
  lens_z=20-sensor_lens_depth;
  translate([sensor_x0,split_y-sensor_w/2,lens_z-sensor_h])
    cube([sensor_l,sensor_w,sensor_h+0.01]);
  hull() for(x=[optic_x,optic_x-(optic_slot_len-optic_d)])
    translate([x,split_y,lens_z-0.1]) cylinder(d=optic_d,h=sensor_lens_depth+0.2);
  translate([sensor_x0-sensor_back_wall-0.1,split_y-sensor_cable_slot/2,lens_z-sensor_h])
    cube([sensor_back_wall+0.2,sensor_cable_slot,sensor_h]);
}
// Quetschrippen: Rückwand (2x) drückt den Sensor gegen die Armspitze,
// Boden (2x) gegen die Linsenseite. Rippen laufen in Druckrichtung (Y).
module sensor_crush_ribs_lower() {
  lens_z=20-sensor_lens_depth;
  for(y=[split_y-sensor_w/2+0.6,split_y+sensor_w/2-1.6])
    translate([sensor_x0-0.01,y,lens_z-sensor_h+1]) cube([sensor_crush+0.01,1,sensor_h-2]);
  for(x=[sensor_x0+3,sensor_x0+sensor_l-4])
    translate([x,split_y-sensor_w/2,lens_z-sensor_h-0.01]) cube([1,sensor_w,sensor_crush+0.01]);
}
module mirror_upper(h) { translate([0,0,h]) mirror([0,0,1]) children(); }

module cavities(h) {
  // Steg
  translate([cavity_x0,cable_y0,low_cav_z0]) cube([cavity_x1-cavity_x0,cable_width,h-2-low_cav_z0]);
  // unterer Arm bis zur Sensor-Rückwand
  translate([cavity_x0,cable_y0,low_cav_z0])
    cube([sensor_x0-sensor_back_wall-cavity_x0,cable_width,low_cav_z1-low_cav_z0]);
  // oberer Arm
  translate([cavity_x0,cable_y0,h-2-up_cav_h])
    cube([sensor_x0-sensor_back_wall-cavity_x0,cable_width,up_cav_h]);
  // Kabeldurchgang ins Gehäuse
  translate([housing_x-1,cable_y0,h/2-cable_height/2]) cube([cavity_x0+1-housing_x+1,cable_width,cable_height]);
}

module sleeve_bores() {
  for(p=sleeve_pos) {
    translate([p[0],frame_y0-0.1,p[1]]) rotate([-90,0,0])
      cylinder(d=sleeve_od+sleeve_fit,h=frame_width+0.2);
    // Senkung für den Hülsenbund in der Rückschale
    translate([p[0],frame_y0-0.1,p[1]]) rotate([-90,0,0])
      cylinder(d=sleeve_collar_d+0.4,h=sleeve_collar_t+0.3);
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
      u_blank(h);
      cavities(h);
      sensor_cuts_lower();
      mirror_upper(h) sensor_cuts_lower();
      sleeve_bores();
      clip_notches(h);
    }
    sensor_crush_ribs_lower();
    mirror_upper(h) sensor_crush_ribs_lower();
  }
}

// Schwalbenschwanz über die ganze Stegtiefe; Buchsenwände 6..10 mm stark
// (v2: 1 mm). Fügen quer zur Trennfuge (in Y), nicht entlang des Stegs.
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
    if(side==1) align_pins(h,lo,hi,0.3);
  }
}

// Druckanlage: breite Außenfläche aufs Bett, Kabelraum zeigt nach oben.
module u_print(h,segment=0,side=0) {
  lo=seg_lo(h,side,segment);
  hi=seg_hi(h,side,segment);
  if(side==0) translate([-frame_back+5,hi+dt_len+5,-frame_y0]) rotate([90,0,0]) u_half(h,segment,side);
  else translate([-frame_back+5,-lo+5,frame_y1]) rotate([-90,0,0]) u_half(h,segment,side);
}

// --------------------------------------------------------------------
// Geführte Schraubhülse: wird von der Rückschale durch beide Halbschalen
// gesteckt, überbrückt den Kabelraum (Kabel laufen darüber vorbei) und
// dient gleichzeitig als Passstift. Innen M4-Durchgang.
module sleeve() {
  difference() {
    union() {
      cylinder(d=sleeve_od,h=frame_width-0.3);
      cylinder(d=sleeve_collar_d,h=sleeve_collar_t);
    }
    translate([0,0,-0.1]) cylinder(d=sleeve_id,h=frame_width+1);
  }
}
module sleeves_on_frame() {
  for(p=sleeve_pos) translate([p[0],frame_y0,p[1]]) rotate([-90,0,0]) sleeve();
}

// Tropfenform für waagerechte Bohrungen im Druck (45°-Dach, nach +Z).
module teardrop_y(d,len) {
  rotate([-90,0,0]) linear_extrude(height=len)
    union() {
      circle(d=d);
      rotate([0,0,225]) square(d/2);
    }
}

// --------------------------------------------------------------------
// Start-Fuß: Schuh unter dem unteren Arm. Breite Flansche (Y) liegen
// bündig mit der U-Innenfläche Z=20 unter der Bahn und stützen seitlich;
// Dreiecksstützen laufen am Steg hoch. Druck: Boden aufs Bett, ohne Support.
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
      // Rückwand am Steg, bis Oberkante Stützen
      if(foot_gusset_h>0) translate([x0,yi0-t,19.9]) cube([t,yi1-yi0+2*t,foot_gusset_h+0.1]);
    }
    // Aufnahme für den U-Boden und den Steg
    translate([frame_back-c,yi0,foot_bottom-c]) cube([arm+1-frame_back+c,yi1-yi0,20+foot_gusset_h+1-foot_bottom+c]);
    // Senkrechte Holzschrauben in die Bahnunterseite (Kopf unten)
    for(y=[(fy0+yi0-t)/2,(fy1+yi1+t)/2]) for(x=[52,95]) {
      translate([x,y,z0-0.1]) cylinder(d=foot_screw_d,h=20.2-z0);
      translate([x,y,z0-0.1]) cylinder(d=foot_screw_head_d,h=20-z0-foot_screw_keep+0.1);
    }
    // Durchgang für die M4-Bolzen durch die Hülsen
    for(p=sleeve_pos) {
      translate([p[0],fy0-0.1,p[1]]) teardrop_y(sleeve_id+0.2,fy1-fy0+0.2);
      translate([p[0],fy0-0.1,p[1]]) teardrop_y(sleeve_collar_d+1,foot_flange+0.1);
      translate([p[0],yi1+t,p[1]]) teardrop_y(sleeve_collar_d+1,foot_flange+0.1);
    }
  }
}
module start_foot_print() { translate([-frame_back+foot_clear+foot_wall,
  foot_flange+foot_wall+foot_clear-frame_y0,-(foot_bottom-foot_clear-foot_wall)]) start_foot(); }

// --------------------------------------------------------------------
// Klammern wie v2 (Breite folgt frame_width).
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
  color([0.9,0.8,0.2]) translate([0,-exploded,0]) sleeves_on_frame();
  sensor_mock(h);
  if(with_foot) color([0.2,0.55,0.75]) translate([0,0,-exploded*2]) start_foot();
  housing_on_frame(h) {
    color([0.15,0.25,0.35]) translate([0,-exploded,0]) housing_base();
    color([0.95,0.5,0.12]) translate([0,exploded,0]) housing_lid();
  }
}
