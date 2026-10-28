#!/usr/bin/env python3
"""Schematische Konstruktionsskizze Pinewood-U v3 (SVG). Maße aus pinewood_v3_shared.scad."""
import math, sys

W, H = 2000, 1400
out = []
def add(s): out.append(s)

INK = "#1d2733"; GREY = "#6b7785"; SHELL0 = "#4a5866"; SHELL1 = "#8a97a5"
FOOT = "#2f80b5"; HOUS = "#25384b"; ORANGE = "#e07a1f"; RED = "#d62828"; BLUE = "#1f5fd6"
WOOD = "#d9b98c"; SENS = "#111"; YEL = "#e2c227"

class P:
    """Panel: mm -> px, origin (ox,oy) in px = mm point (mx,my); y up in mm."""
    def __init__(s, ox, oy, sc, mx=0, my=0, rot=0):
        s.ox, s.oy, s.sc, s.mx, s.my, s.rot = ox, oy, sc, mx, my, math.radians(rot)
    def p(s, x, y):
        x -= s.mx; y -= s.my
        c, n = math.cos(s.rot), math.sin(s.rot)
        x, y = x*c - y*n, x*n + y*c
        return (s.ox + x*s.sc, s.oy - y*s.sc)
    def poly(s, pts, fill="none", stroke=INK, sw=2, dash=None, op=1):
        d = " ".join(f"{a:.1f},{b:.1f}" for a, b in (s.p(*q) for q in pts))
        da = f' stroke-dasharray="{dash}"' if dash else ""
        add(f'<polygon points="{d}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}"{da} fill-opacity="{op}" stroke-linejoin="round"/>')
    def rect(s, x0, y0, x1, y1, **k):
        s.poly([(x0, y0), (x1, y0), (x1, y1), (x0, y1)], **k)
    def line(s, pts, stroke=INK, sw=2, dash=None, marker=None):
        d = " ".join(f"{a:.1f},{b:.1f}" for a, b in (s.p(*q) for q in pts))
        da = f' stroke-dasharray="{dash}"' if dash else ""
        mk = f' marker-end="url(#{marker})"' if marker else ""
        add(f'<polyline points="{d}" fill="none" stroke="{stroke}" stroke-width="{sw}"{da}{mk} stroke-linejoin="round" stroke-linecap="round"/>')
    def circle(s, x, y, r, fill="none", stroke=INK, sw=2):
        a, b = s.p(x, y)
        add(f'<circle cx="{a:.1f}" cy="{b:.1f}" r="{r*s.sc:.1f}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}"/>')

def text(x, y, t, size=17, anchor="start", weight="normal", color=INK, italic=False):
    st = ' font-style="italic"' if italic else ""
    for i, ln in enumerate(t.split("\n")):
        add(f'<text x="{x:.1f}" y="{y+i*size*1.22:.1f}" font-family="DejaVu Sans, Arial" font-size="{size}" '
            f'text-anchor="{anchor}" font-weight="{weight}" fill="{color}"{st}>{esc(ln)}</text>')
def esc(t): return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

def label(P_, mx, my, tx, ty, t, size=15, color=INK):
    """Leader from mm point (mx,my) to a left-aligned text block starting at px (tx,ty)."""
    a, b = P_.p(mx, my)
    add(f'<circle cx="{a:.1f}" cy="{b:.1f}" r="3.5" fill="{color}"/>')
    lines = t.split("\n")
    wpx = max(len(l) for l in lines) * size * 0.56
    ex = tx - 5 if tx > a else tx + wpx + 5
    add(f'<polyline points="{a:.1f},{b:.1f} {ex:.1f},{ty-5:.1f}" fill="none" stroke="{color}" stroke-width="1.3"/>')
    text(tx, ty, t, size, "start", color=color)

def dim_v(P_, x, y0, y1, t, off=0, size=15):
    (a, b0), (_, b1) = P_.p(x, y0), P_.p(x, y1)
    a += off
    add(f'<line x1="{a:.1f}" y1="{b0:.1f}" x2="{a:.1f}" y2="{b1:.1f}" stroke="{GREY}" stroke-width="1.4" marker-start="url(#tick)" marker-end="url(#tick)"/>')
    add(f'<text x="{a-6:.1f}" y="{(b0+b1)/2:.1f}" font-family="DejaVu Sans" font-size="{size}" fill="{GREY}" text-anchor="middle" transform="rotate(-90 {a-6:.1f} {(b0+b1)/2:.1f})">{esc(t)}</text>')
def dim_h(P_, y, x0, x1, t, off=0, size=15):
    (a0, b), (a1, _) = P_.p(x0, y), P_.p(x1, y)
    b += off
    add(f'<line x1="{a0:.1f}" y1="{b:.1f}" x2="{a1:.1f}" y2="{b:.1f}" stroke="{GREY}" stroke-width="1.4" marker-start="url(#tick)" marker-end="url(#tick)"/>')
    add(f'<text x="{(a0+a1)/2:.1f}" y="{b-6:.1f}" font-family="DejaVu Sans" font-size="{size}" fill="{GREY}" text-anchor="middle">{esc(t)}</text>')

def frame_box(x, y, w, h, title, num):
    add(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="10" fill="#ffffff" stroke="#c9d1da" stroke-width="1.5"/>')
    add(f'<circle cx="{x+24}" cy="{y+26}" r="14" fill="{INK}"/>')
    text(x+24, y+32, num, 17, "middle", "bold", "#fff")
    text(x+48, y+33, title, 20, weight="bold")

add(f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">')
add('''<defs>
<marker id="arr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="context-stroke"/></marker>
<marker id="tick" viewBox="0 0 10 10" refX="5" refY="5" markerWidth="10" markerHeight="10" orient="auto"><path d="M5,0 L5,10" stroke="#6b7785" stroke-width="2"/></marker>
<pattern id="wood" width="10" height="10" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect width="10" height="10" fill="#e8d3b0"/><line x1="0" y1="0" x2="0" y2="10" stroke="#b8956a" stroke-width="2"/></pattern>
<pattern id="hatch" width="7" height="7" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect width="7" height="7" fill="#dfe5eb"/><line x1="0" y1="0" x2="0" y2="7" stroke="#8a97a5" stroke-width="1.3"/></pattern>
</defs>''')
add(f'<rect width="{W}" height="{H}" fill="#f4f6f8"/>')
text(30, 50, "Pinewood-U v3 – Konstruktionsskizze (schematisch, Start-Variante links)", 30, weight="bold")
text(30, 82, "Maße in mm aus pinewood_v3_shared.scad. Rot gestrichelt = Lichtschranke, orange gestrichelt = Kabelweg. Keine maßstäbliche Fertigungszeichnung.", 17, color=GREY)

# ---------------- Panel 1: Vorderansicht ----------------
frame_box(20, 100, 760, 1280, "Vorderansicht Start-U an der Bahn", "1")
p = P(395, 1240, 2.4, 0, -40)
h = 340
# Bahn (unter dem oberen Arm, auf dem unteren Arm)
p.rect(20, 20, 150, 38, fill="url(#wood)", stroke="#8b6a43")
text(*p.p(128, 60), "Bahn (Holz)", 15, "middle", color="#7a5a33")
p.rect(91.2, 20, 98.8, 38, fill="#f4f6f8", stroke="#8b6a43", sw=1.2)  # Loch
# Start-Fuß (dahinter, gestrichelt)
p.rect(-16.3, -24.3, 105, 20, fill=FOOT, stroke=FOOT, op=0.25, sw=2)
p.rect(-16.3, 20, 20, 80, fill=FOOT, stroke=FOOT, op=0.18, sw=2, dash="7 5")
# Rahmen
p.poly([(-12, -20), (105, -20), (105, 20), (20, 20), (20, h-20), (105, h-20), (105, h+2), (-12, h+2)], fill=SHELL0, stroke=INK, sw=2.5)
# Kabelraum (verdeckt)
p.poly([(-5, -8), (75.8, -8), (75.8, 12), (10, 12), (10, h-14), (75.8, h-14), (75.8, h-2), (-5, h-2)], fill="none", stroke="#c5ced8", sw=1.5, dash="6 4")
# Trennstellen
for z in [80, 260]:
    p.line([(-12, z), (20, z)], stroke="#dce3ea", sw=2)
for z in [125, 215]:
    p.line([(-12, z), (20, z)], stroke="#dce3ea", sw=2, dash="3 4")
# Gehäuse
p.rect(-65.5, 125, -8.5, 215, fill=HOUS, stroke=INK, sw=2)
p.rect(-65.5, 125, -62.5, 215, fill=ORANGE, stroke=INK, sw=1.2)
# Sensoren
p.rect(78.2, 6.1, 98, 14, fill=SENS, stroke=SENS)
p.rect(78.2, h-14, 98, h-6.1, fill=SENS, stroke=SENS)
# Lichtstrahl
p.line([(93, 14), (93, h-14)], stroke=RED, sw=2.5, dash="9 6")
# Hülsen
for x in [35, 70]:
    p.circle(x, -3, 4, fill=YEL, stroke=INK, sw=1.5); p.circle(x, -3, 2.25, fill="#fff", stroke=INK, sw=1)
# Kabelweg
p.line([(78, 10), (40, 6), (2.5, 6), (2.5, h/2), (-8.5, h/2)], stroke=ORANGE, sw=3, dash="8 5")
p.line([(78, h-10), (40, h-8), (2.5, h-8), (2.5, h/2+4), (-8.5, h/2+4)], stroke=ORANGE, sw=3, dash="8 5")
# Auto
p.poly([(118, 38), (160, 38), (160, 46), (150, 52), (125, 52), (118, 46)], fill="#c0392b", stroke=INK, sw=1.5)
p.circle(126, 38, 3.2, fill=INK); p.circle(152, 38, 3.2, fill=INK)
add(f'<line x1="{p.p(165,45)[0]:.0f}" y1="{p.p(165,45)[1]:.0f}" x2="{p.p(108,45)[0]:.0f}" y2="{p.p(108,45)[1]:.0f}" stroke="#c0392b" stroke-width="2" marker-end="url(#arr)"/>')
# Zwinge
cx = 55
p.line([(cx, 38), (cx, 46), (cx+0.01, 46)], stroke=GREY, sw=5)
p.poly([(cx-6, 38), (cx+6, 38), (cx+6, 42), (cx-6, 42)], fill=GREY, stroke=GREY)
p.line([(cx-14, 46), (cx-14, -36), (cx, -36), (cx, -28)], stroke=GREY, sw=5)
p.poly([(cx-6, -28), (cx+6, -28), (cx+6, -24.3), (cx-6, -24.3)], fill=GREY, stroke=GREY)
p.line([(cx-14, 46), (cx, 46)], stroke=GREY, sw=5)
# Maße
dim_v(p, 105, -20, 20, "40", off=26)
dim_v(p, 120, 60, h-14, "Lichtweg 312 (Sensor–Sensor)", off=60)
dim_h(p, h+2, -12, 20, "32", off=-14)
# Beschriftungen
label(p, 88, 10, 600, 1290, "Sensor eingeklemmt (3)", 14)
label(p, 70, -3, 600, 1330, "M4-Hülsen (5)", 14)
label(p, 50, -15, 420, 1345, "U-Boden 40 mm (v2: 22)", 14)
label(p, -14, -20, 40, 1330, "Start-Fuß (blau),\nnur Start", 14, color=FOOT)
label(p, -38, 200, 40, 360, "ESP-Gehäuse,\nKeil-Andockung (6)", 14)
label(p, -12, 80, 40, 990, "Fugen versetzt (4):\nRückschale ——\nVorderschale - -", 14)
label(p, cx-14, -30, 40, 1240, "Schraubzwinge", 14, color=GREY)
label(p, 93, 220, 560, 520, "Lichtschranke", 14, color=RED)
label(p, 2.5, 270, 560, 420, "Kabelraum 15 mm", 14, color=ORANGE)

# ---------------- Panel 2: Seitenansicht geneigt ----------------
frame_box(800, 100, 560, 640, "Seitenansicht Start: Bahn geneigt", "2")
ang = 14
q = P(1040, 640, 1.05, 10, 20, rot=ang)
# Bahn: Ebene Z=20..38 (im U-System), entlang Y
q.rect(-140, 20, 150, 38, fill="url(#wood)", stroke="#8b6a43")
# Fuß Querschnitt (YZ): Y Mitte 10
y0, y1 = -5.5, 25.5
fy0, fy1 = y0-0.3-4-25, y1+0.3+4+25
q.poly([(fy0, -24.3), (fy1, -24.3), (fy1, 20), (y1+4.3, 20), (y1+0.3, 80), (y0-0.3, 80), (y0-4.3, 80), (fy0, 20)], fill=FOOT, stroke=INK, sw=2, op=0.9)
# Rahmen
q.rect(y0, -20, y1, 342, fill=SHELL0, stroke=INK, sw=2)
q.rect(10, -20, y1, 342, fill=SHELL1, stroke=INK, sw=2)
q.rect(10-7.5, -8, 10+7.5, 330, fill="none", stroke="#c5ced8", sw=1.3, dash="5 4")
# blaue Stützlinie wie in der Skizze
q.line([(fy1, -24.3), (y1+4.3, 80)], stroke=BLUE, sw=3.5)
q.line([(fy0, -24.3), (y0-4.3, 80)], stroke=BLUE, sw=3.5)
# Lot
a, b = q.p(10, -24.3)
add(f'<line x1="{a:.0f}" y1="{b:.0f}" x2="{a:.0f}" y2="{b-330:.0f}" stroke="{GREY}" stroke-width="1.3" stroke-dasharray="4 4"/>')
text(a+8, b-300, "Lot", 14, color=GREY)
# Gegendruck-Pfeile
for s_, yy in [(-1, fy0+6), (1, fy1-6)]:
    a, b = q.p(yy, 20)
    add(f'<line x1="{a:.0f}" y1="{b+70:.0f}" x2="{a:.0f}" y2="{b+8:.0f}" stroke="{BLUE}" stroke-width="3" marker-end="url(#arr)"/>')
label(q, fy1-3, 5, 1180, 560, "Flansch je 25 mm,\nbündig unter der Bahn", 14, color=FOOT)
label(q, y1+12, 50, 1150, 400, "Stützdreiecke 60 mm\n(= blaue Linie\naus deiner Skizze)", 14, color=BLUE)
label(q, fy0+8, -10, 820, 700, "4 Holzschrauben von\nunten in die Bahn", 14)
label(q, 10, 250, 820, 180, "U steht senkrecht\nzur Bahnfläche", 14)
dim_h(q, -24.3, fy0, fy1, "≈ 90", off=34)

# ---------------- Panel 3: Sensortasche ----------------
frame_box(1380, 100, 600, 640, "Sensortasche (Schnitt durch Armspitze)", "3")
r = P(1405, 560, 7.4, 66, -8)
r.rect(66, -8, 105, 20, fill="url(#hatch)", stroke=INK, sw=2)
r.rect(66, -8, 75.8, 12, fill="#fff", stroke=INK, sw=1.5)        # Kabelraum
r.rect(75.8, 6.1, 78.2, 14, fill="#fff", stroke=INK, sw=1.2)      # Kabelschlitz
r.rect(78.2, 6.1, 98, 14, fill="#fff", stroke=INK, sw=2)          # Tasche
r.rect(78.45, 6.35, 97.8, 13.9, fill=SENS, stroke=SENS)           # Sensor
r.rect(89.3, 14, 100.85, 20, fill="#fff", stroke=INK, sw=1.5)     # Lichtschlitz
for x in [81.2, 93.2]:
    r.rect(x, 6.1, x+1, 6.35, fill=RED, stroke=RED, sw=1)
r.rect(78.2, 7.1, 78.45, 13, fill=RED, stroke=RED, sw=1)
r.line([(93, 14), (93, 24)], stroke=RED, sw=3, dash="7 5")
r.line([(70, 4), (77, 9.5), (79, 10)], stroke=ORANGE, sw=3)
label(r, 77, 13.5, 1400, 190, "Rückwand 2,4 mm mit 5-mm-Kabelschlitz\n(v2: hinten offen → Sensor rutschte)", 14)
label(r, 88, 10, 1715, 300, "Tasche 19,8×9,9×7,9\n(Maße aus v1)", 14)
label(r, 81.7, 6.2, 1715, 610, "Quetschrippen\n0,25 mm (rot)", 14, color=RED)
label(r, 95, 18, 1715, 240, "Lichtschlitz", 14)
label(r, 101, -4, 1715, 500, "Spitze massiv", 14)
label(r, 70, 2, 1400, 650, "Kabelraum unterer Arm (20 mm hoch)", 14)
text(1400, 690, "Die Trennfuge läuft mittig durch die Tasche: Sensor einlegen,\nzweite Schale aufsetzen → rundum eingeklemmt.", 13, color=GREY)

# ---------------- Panel 4: Versetzte Fugen + Schwalbenschwanz ----------------
frame_box(800, 760, 560, 620, "Versetzte Fugen + kräftiger Schwalbenschwanz", "4")
sc = 1.25; bx = 880; by = 1340
def zz(z): return by - (z+20)*sc
for i, (js, col, nm) in enumerate([([80, 260], SHELL0, "Rückschale"), ([125, 215], SHELL1, "Vorderschale")]):
    x = bx + i*48
    edges = [-20] + js + [342]
    for k in range(len(edges)-1):
        add(f'<rect x="{x}" y="{zz(edges[k+1])+1.5:.1f}" width="40" height="{(edges[k+1]-edges[k])*sc-3:.1f}" fill="{col}" stroke="{INK}" stroke-width="1.5"/>')
    text(x+20, zz(342)-10-(i*16), nm, 13, "middle")
    for j in js:
        text(x+(-6 if i == 0 else 46), zz(j)+5, str(j), 13, "end" if i == 0 else "start", color=GREY)
text(bx+44, by+22, "Trennhöhen Start (mm)", 13, "middle", color=GREY)
# Schwalbenschwanz-Detail
d = P(1150, 1230, 5.0, 4, 0)
d.rect(-12, -14, 20, 0, fill=SHELL0, stroke=INK, sw=2)
d.poly([(-2, 0), (10, 0), (14, 14), (-6, 14)], fill=SHELL0, stroke=INK, sw=2)
d.poly([(-12, 0.6), (-2.3, 0.6), (-6.3, 14.3), (14.3, 14.3), (10.3, 0.6), (20, 0.6), (20, 32), (-12, 32)], fill=SHELL1, stroke=INK, sw=2, op=0.85)
dim_h(d, 22, -12, -6.3, "5,7", off=0, size=14)
dim_h(d, 22, 14.3, 20, "5,7", off=0, size=14)
dim_h(d, -18, -12, 20, "Stegtiefe 32 (v2: 28)", off=0, size=14)
text(1150, 1035, "Buchsenwand ≥ 5,7 mm (v2: ≈ 1 mm)", 14, "middle", "bold")
text(1150, 1365, "einlegen quer zur Fuge, dann verkleben", 13, "middle", color=GREY)
text(1010, 830, "Jede Fuge wird von der anderen\nSchale überdeckt → große Klebe-\nflächen, kein Knickpunkt.\nAußenwand je Schale 8 mm (v2: 4).", 14)

# ---------------- Panel 5: Hülse ----------------
frame_box(1380, 760, 600, 300, "Geführte Schraubhülse (Schnitt unterer Arm)", "5")
e = P(1470, 1040, 3.0, -14, -24)
e.rect(-5.5, -20, 10, 20, fill=SHELL0, stroke=INK, sw=2)
e.rect(10, -20, 25.5, 20, fill=SHELL1, stroke=INK, sw=2)
e.rect(2.5, -8, 17.5, 12, fill="#fff", stroke=INK, sw=1.5)
e.rect(-5.5, -7, 25.2, 1, fill=YEL, stroke=INK, sw=1.5)
e.rect(-5.5, -9, -3.5, 3, fill=YEL, stroke=INK, sw=1.5)
e.rect(-14, -5.7, 34, -0.3, fill="#9aa4ae", stroke=INK, sw=1.2)   # Bolzen
e.rect(-14, -9, -10, 3, fill="#9aa4ae", stroke=INK, sw=1.2)
e.rect(25.5, -24, 28.5, 20, fill="#b7bec6", stroke=INK, sw=1.5)   # L-Winkel
e.rect(28.5, 20, 55, 23, fill="#b7bec6", stroke=INK, sw=1.5)
e.rect(-5.5, 23, 55, 37, fill="url(#wood)", stroke="#8b6a43")
for k in range(3):
    e.circle(6+k*3.5, 9, 1.2, fill=ORANGE, stroke=ORANGE, sw=1)
label(e, 12, 1, 1690, 840, "Hülse Ø8 / Ø4,5\nüberbrückt den\nKabelraum", 13)
label(e, 27, -10, 1690, 960, "Metall-L-Winkel\nan der Bahn", 13)
label(e, -12, -3, 1400, 1045, "M4-Bolzen", 13)
text(1400, 1050, "", 1)

# ---------------- Panel 6: Andockung ----------------
frame_box(1380, 1080, 600, 300, "Gehäuse-Andockung mit Keilrampe", "6")
f = P(1400, 1330, 4.0, 20, -8)
f.rect(20, -6.2, 85, 6.2, fill="#dfe5eb", stroke=INK, sw=1.5)
f.rect(28, -6.2, 51.4, 6.2, fill="#fff", stroke=INK, sw=1.5)
f.rect(51.4, -3.1, 66.7, 3.1, fill="#fff", stroke=INK, sw=1.5)
f.poly([(51.6, 3.1), (59.6, 6.2), (66.7, 6.2), (66.7, 3.1)], fill=RED, stroke=RED, sw=1, op=0.6)
f.poly([(51.6, -3.1), (59.6, -6.2), (66.7, -6.2), (66.7, -3.1)], fill=RED, stroke=RED, sw=1, op=0.6)
f.rect(43.5, -3, 66.5, 3, fill=SHELL0, stroke=INK, sw=1.5, op=0.75)
add(f'<line x1="{f.p(30,12)[0]:.0f}" y1="{f.p(30,12)[1]:.0f}" x2="{f.p(60,12)[0]:.0f}" y2="{f.p(60,12)[1]:.0f}" stroke="{INK}" stroke-width="2.5" marker-end="url(#arr)"/>')
text(f.p(45, 14)[0], f.p(45, 14)[1], "einschieben 27 mm", 14, "middle")
label(f, 63, 5, 1720, 1170, "Keilrampe 0→0,45:\n0,1 mm Vorspannung\n(v2: 0,4–0,6 Spiel)", 13, color=RED)
label(f, 60, -2.5, 1720, 1300, "Schlitz 6,2 für\n6-mm-Steg (v2: 6,6)", 13)

add('</svg>')
open(sys.argv[1], "w").write("\n".join(out))
