#!/usr/bin/env python3
"""Builds the v3 assembly guide as HTML (de/en). Turn it into a PDF with
any browser ("Print to PDF", A4, background graphics on).

Usage: python3 assembly_guide_v3.py   -> assembly_guide_v3_de.html, assembly_guide_v3_en.html
Images are taken from images/ and design_sketch_v3_<lang>.png.
"""
import html

T = {
"title": {"de": "Pinewood-U v3 – Montageanleitung", "en": "Pinewood U v3 – Assembly Guide"},
"subtitle": {"de": "Lichtschranken-Halter (Adafruit 2167) für die zweispurige Pinewood-Derby-Bahn. Start (340 mm, auf der geneigten Rampe) und Ziel (240 mm, gerade).",
             "en": "Light barrier holder (Adafruit 2167) for the two-lane pinewood derby track. Start (340 mm, on the inclined ramp) and finish (240 mm, level)."},
"fig_start": {"de": "Start-Einheit mit Fuß", "en": "Start unit with foot"},
"fig_finish": {"de": "Ziel-Einheit", "en": "Finish unit"},
"h_parts": {"de": "1. Teileliste", "en": "1. Parts list"},
"parts_intro": {"de": "Jede Einheit besteht aus einer Rückschale (dunkel, mit Passstiften) und einer Vorderschale (hell, mit Mutteraufnahme). Beide Schalen werden immer gebraucht. Die STL-Dateien liegen in <code>stl/</code>.",
                "en": "Every unit consists of a back shell (dark, with alignment pins) and a front shell (light, with nut recess). Both shells are always needed. The STL files are in <code>stl/</code>."},
"col_part": {"de": "Teil", "en": "Part"}, "col_file": {"de": "STL-Datei", "en": "STL file"},
"col_start": {"de": "Start", "en": "Start"}, "col_finish": {"de": "Ziel", "en": "Finish"},
"p_back": {"de": "Rückschale, Segmente", "en": "Back shell, segments"},
"p_front": {"de": "Vorderschale, Segmente", "en": "Front shell, segments"},
"p_foot": {"de": "Start-Fuß", "en": "Start foot"},
"p_spine": {"de": "Klammer Steg", "en": "Spine clip"},
"p_arm": {"de": "Klammer oberer Arm", "en": "Upper arm clip"},
"p_hbase": {"de": "Elektronikgehäuse", "en": "Electronics housing"},
"p_hlid": {"de": "Gehäusedeckel", "en": "Housing lid"},
"p_lock": {"de": "Sperrstopfen Andockung", "en": "Docking lock plug"},
"hand": {"de": "<b>Links/rechts:</b> <code>*_right</code> ist das komplette Spiegelbild von <code>*_left</code>. Stehen die beiden U-Rahmen eines Tores an den gegenüberliegenden Außenseiten der Bahn, braucht jedes Tor einen linken und einen rechten Rahmen, also insgesamt <code>start_left</code>, <code>start_right</code>, <code>finish_left</code> und <code>finish_right</code>. Stehen beide auf derselben Seite, reicht zweimal dieselbe Hand. Klammern, Gehäuse und Stopfen sind nicht seitenabhängig.",
         "en": "<b>Left/right:</b> <code>*_right</code> is the complete mirror image of <code>*_left</code>. If the two U-frames of a gate stand on opposite outer sides of the track, each gate needs one left and one right frame, i.e. <code>start_left</code>, <code>start_right</code>, <code>finish_left</code> and <code>finish_right</code> in total. If both stand on the same side, use the same hand twice. Clips, housing and lock plugs are not handed."},
"total": {"de": "Für die übliche Bahn (2 Spuren, Start und Ziel, Rahmen gegenüber) sind das 4 Einheiten: 20 Schalensegmente, 2 Füße, 8 Steg-Klammern, 4 Arm-Klammern, 4 Gehäuse mit Deckel und 8 Sperrstopfen.",
          "en": "For the usual track (2 lanes, start and finish, frames facing each other) that is 4 units: 20 shell segments, 2 feet, 8 spine clips, 4 arm clips, 4 housings with lid and 8 lock plugs."},
"h_hw": {"de": "Zukaufteile und Werkzeug", "en": "Hardware and tools"},
"hw": {"de": ["Je Einheit 2 Metallwinkel, die dauerhaft an der Bahn bleiben (Annahme: 20 mm breit, Loch für M5, Lochabstand 35 mm).",
               "Ziel: 2 × M5×40 mit Mutter. Start: 2 × M5×45 mit Mutter. Die Mutter sitzt versenkt in der Vorderschale.",
               "Optional nur Start: 4 × Holzschraube 4×30, nur zur Unterstützung.",
               "Kleber für PETG (z. B. 2K-Epoxid oder Sekundenkleber mit Aktivator), kleiner Schraubendreher, Schlüssel SW 8."],
        "en": ["Per unit 2 metal angles that stay on the track permanently (assumed: 20 mm wide, M5 hole, 35 mm hole spacing).",
               "Finish: 2 × M5×40 with nut. Start: 2 × M5×45 with nut. The nut sits recessed in the front shell.",
               "Optional, start only: 4 × wood screw 4×30, for support only.",
               "Glue for PETG (e.g. 2-part epoxy or CA glue with activator), small screwdriver, 8 mm spanner."]},
"h_print": {"de": "2. Drucken", "en": "2. Printing"},
"print": {"de": ["Alle STLs sind bereits in Drucklage exportiert und brauchen keinen Support.",
                  "Empfehlung: PETG, 0,4-mm-Düse, 0,2-mm-Schichten, 4 Perimeter, 25–35 % Infill.",
                  "Schalen: breite Außenseite aufs Bett, Kabelraum und Sensortasche zeigen nach oben.",
                  "Start-Fuß: Boden aufs Bett. Die waagerechten Bolzenlöcher sind tropfenförmig (45°-Dach).",
                  "Größtes Teil: Ziel-Vorderschale Segment 1, 127 × 204 mm. Alles passt auf ein 250-mm-Bett."],
          "en": ["All STLs are exported in print orientation and need no supports.",
                 "Recommended: PETG, 0.4 mm nozzle, 0.2 mm layers, 4 perimeters, 25–35 % infill.",
                 "Shells: wide outer face on the bed, cable channel and sensor pocket facing up.",
                 "Start foot: bottom on the bed. The horizontal bolt holes are teardrop-shaped (45° roof).",
                 "Largest part: finish front shell segment 1, 127 × 204 mm. Everything fits a 250 mm bed."]},
"h_test": {"de": "3. Zuerst Probedrucke", "en": "3. Test prints first"},
"test": {"de": ["<code>test_sensor_seat.stl</code> (4 kleine Teile): Sensor in den 1-mm-Sitz der Rückschale drücken, Vorderschale aufsetzen, oben und unten prüfen. Er soll gerade und ohne Wackeln sitzen. Zu locker: <code>sensor_seat_depth</code> 1,5–2. Schräg nach dem Zusammenfügen: <code>sensor_front_relief</code> 0,25.",
                 "<code>test_dovetail.stl</code>: Segmente quer zur Fuge ineinanderschieben. Klemmt es: <code>fit</code> 0,35.",
                 "Andockung am fertigen Gehäuse prüfen. Zu stramm: <code>dock_ramp_h</code> 0,4. Noch Spiel: 0,5."],
         "en": ["<code>test_sensor_seat.stl</code> (4 small parts): press the sensor into the 1 mm seat of the back shell, add the front shell, check top and bottom. It must sit straight and without wobble. Too loose: <code>sensor_seat_depth</code> 1.5–2. Tilted after joining: <code>sensor_front_relief</code> 0.25.",
                "<code>test_dovetail.stl</code>: slide the segments together across the joint. If it binds: <code>fit</code> 0.35.",
                "Check the docking on the printed housing. Too tight: <code>dock_ramp_h</code> 0.4. Still play: 0.5."]},
"h_expl": {"de": "4. Explosionszeichnung Start-Einheit", "en": "4. Exploded view of the start unit"},
"legend": {"de": ["Rückschale (dunkel), 3 Segmente", "Vorderschale (hell), 3 Segmente, Fugen versetzt", "Sensoren (schwarz), oben und unten an den Armspitzen", "Klammern (orange): 2 am Steg, 1 am oberen Arm", "Elektronikgehäuse mit Deckel, an der Stegrückseite angedockt", "Start-Fuß (blau), nur beim Start"],
           "en": ["Back shell (dark), 3 segments", "Front shell (light), 3 segments, staggered joints", "Sensors (black) at the upper and lower arm tips", "Clips (orange): 2 on the spine, 1 on the upper arm", "Electronics housing with lid, docked to the back of the spine", "Start foot (blue), start only"]},
"h_steps": {"de": "5. Zusammenbau", "en": "5. Assembly"},
"steps": {"de": [
  ("step_shells", "Rückschale zusammenfügen", "Die Segmente der Rückschale quer zur Fuge ineinanderschieben (Schwalbenschwanz), trocken prüfen, dann verkleben. Genauso die Vorderschale. Die Fugen der beiden Schalen liegen versetzt, jede Fuge wird von der anderen Schale überdeckt."),
  ("step_sensor", "Sensoren einsetzen", "Die Linsenseite jedes Sensors in den 1 mm tiefen Sitz der Rückschale drücken, oben und unten. Dahinter ist es offen: Kabel durch den Kabelraum zum Steg führen. Im unteren Arm laufen die Kabel <b>über</b> die beiden Schraubrohre. Zum Herausnehmen: kleinen Schraubendreher in die Kerbe an der Armspitze setzen und heraushebeln."),
  ("step_close", "Vorderschale aufsetzen", "Vorderschale auf die Passstifte setzen. Prüfen, dass beide Sensoren gerade sitzen, dann großflächig verkleben."),
  ("step_clips", "Klammern aufschieben", "Zwei Klammern auf den Steg und eine auf den oberen Arm schieben. Sie halten die Schalen beim Kleben zusammen und bleiben danach dran."),
  ("step_housing", "Gehäuse andocken", "ESP32 im Gehäuse montieren, Kabel durch den Durchlass führen. Gehäuse auf die Zapfen am Steg setzen und 27 mm einschieben, bis es fest auf den Keilrampen sitzt. Dann die zwei Sperrstopfen setzen."),
  ("step_mount_finish", "Ziel befestigen", "Die zwei Metallwinkel bleiben dauerhaft an der Bahn. U an die Winkel halten, 2 × M5×40 von der Winkelseite durch Winkel und U stecken, die Mutter sitzt versenkt in der Vorderschale und dreht nicht mit."),
  ("step_mount_start", "Start befestigen", "U in den Fuß stellen. Mutter durch die große Senkung im Fuß in die Aufnahme der Vorderschale legen. Winkel in die Flansch-Aussparungen, 2 × M5×45 durch Winkel, Fuß und U. Der breite Flansch stützt das U seitlich auf der Rampe. Nur wenn es nicht optimal hält: zusätzlich 4 Holzschrauben 4×30 von unten durch den Flansch."),
  ],
  "en": [
  ("step_shells", "Join the back shell", "Slide the back shell segments together across the joint (dovetail), dry-fit, then glue. Do the same with the front shell. The joints of the two shells are staggered, so every joint is covered by the other shell."),
  ("step_sensor", "Insert the sensors", "Press the lens side of each sensor into the 1 mm deep seat of the back shell, top and bottom. Behind it the pocket is open: route the cables through the cable channel to the spine. In the lower arm the cables run <b>over</b> the two bolt tubes. To remove a sensor, put a small screwdriver into the notch at the arm tip and lever it out."),
  ("step_close", "Add the front shell", "Place the front shell on the alignment pins. Check that both sensors sit straight, then glue over the full surface."),
  ("step_clips", "Slide on the clips", "Slide two clips onto the spine and one onto the upper arm. They hold the shells together while gluing and stay on afterwards."),
  ("step_housing", "Dock the housing", "Mount the ESP32 in the housing and route the cables through the passage. Put the housing onto the studs on the spine and slide it 27 mm until it sits firmly on the wedge ramps. Then insert the two lock plugs."),
  ("step_mount_finish", "Mount the finish", "The two metal angles stay on the track permanently. Hold the U against the angles and push 2 × M5×40 from the angle side through angle and U; the nut sits recessed in the front shell and cannot turn."),
  ("step_mount_start", "Mount the start", "Stand the U in the foot. Drop the nut through the large counterbore in the foot into the recess of the front shell. Put the angles into the flange slots and push 2 × M5×45 through angle, foot and U. The wide flange supports the U sideways on the ramp. Only if it does not hold well enough: add 4 wood screws 4×30 from below through the flange."),
  ]},
"h_swap": {"de": "6. Tauschen und Wartung", "en": "6. Swapping and maintenance"},
"swap": {"de": ["Tauschen: 2 Schrauben lösen und das U abnehmen, beim Start zusammen mit dem Fuß. An der Bahn bleibt alles, wie es ist.",
                 "Ist der Start-Fuß zusätzlich angeschraubt, müssen auch die Holzschrauben raus: Der Fuß umschließt den U-Boden, darüber liegt die Bahn.",
                 "Alle Maße stehen in <code>pinewood_v3_shared.scad</code>. Nach jeder Änderung <code>./export_stls_v3.sh</code> neu ausführen."],
         "en": ["Swapping: undo 2 bolts and take the U off, at the start together with the foot. Nothing on the track is changed.",
                "If the start foot is also screwed on, the wood screws must come out too: the foot wraps around the U bottom and the track lies above it.",
                "All dimensions are in <code>pinewood_v3_shared.scad</code>. Run <code>./export_stls_v3.sh</code> again after every change."]},
"h_sketch": {"de": "7. Konstruktionsskizze", "en": "7. Design sketch"},
}

CSS = """
@page { size: A4; margin: 14mm 14mm 16mm 14mm; }
body { font-family: 'DejaVu Sans', Arial, sans-serif; color: #1d2733; font-size: 10.5pt; line-height: 1.45; }
h1 { font-size: 22pt; margin: 0 0 4px; } h2 { font-size: 14pt; margin: 18px 0 6px; border-bottom: 2px solid #e06a10; padding-bottom: 3px; }
h3 { font-size: 11.5pt; margin: 0 0 4px; }
.sub { color: #555f6b; margin-bottom: 12px; }
.row { display: flex; gap: 16px; align-items: flex-end; justify-content: center; }
figure { margin: 0; text-align: center; } figcaption { font-size: 9pt; color: #555f6b; }
table { border-collapse: collapse; width: 100%; font-size: 9.5pt; }
th, td { border: 1px solid #c9d1da; padding: 3px 6px; text-align: left; } th { background: #eef2f6; }
td.n { text-align: center; width: 48px; }
code { font-size: 9pt; background: #eef2f6; padding: 0 3px; border-radius: 3px; }
.step { display: flex; gap: 14px; align-items: center; border: 1px solid #dde3ea; border-radius: 8px; padding: 10px; margin: 0 0 10px; page-break-inside: avoid; }
.step img { max-height: 260px; max-width: 300px; } .step .txt { flex: 1; }
.num { display: inline-block; width: 22px; height: 22px; border-radius: 11px; background: #1d2733; color: #fff; text-align: center; font-weight: bold; margin-right: 6px; }
.pb { page-break-before: always; }
.legend li { margin-bottom: 4px; }
@page land { size: A4 landscape; margin: 10mm; }
.land { page: land; }
"""

def build(lang):
    t = lambda k: T[k][lang]
    rows = [("p_back", "{u}_back_shell_seg1..3", "3", "{f}_back_shell_seg1..2", "2"),
            ("p_front", "{u}_front_shell_seg1..3", "3", "{f}_front_shell_seg1..2", "2"),
            ("p_foot", "{u}_foot", "1", "–", "–"),
            ("p_spine", "spine_clip", "2", "spine_clip", "2"),
            ("p_arm", "arm_clip", "1", "arm_clip", "1"),
            ("p_hbase", "housing_base", "1", "housing_base", "1"),
            ("p_hlid", "housing_lid", "1", "housing_lid", "1"),
            ("p_lock", "dock_lock", "2", "dock_lock", "2")]
    tr = "".join(
        f"<tr><td>{t(k)}</td><td><code>{a.format(u='start_&lt;hand&gt;')}</code></td><td class=n>{b}</td>"
        f"<td><code>{c.format(f='finish_&lt;hand&gt;')}</code></td><td class=n>{d}</td></tr>" for k, a, b, c, d in rows)
    li = lambda xs: "<ul>" + "".join(f"<li>{x}</li>" for x in xs) + "</ul>"
    steps = "".join(
        f"<div class=step><img src='images/{img}.png'><div class=txt><h3><span class=num>{i+1}</span>{html.escape(h)}</h3>{p}</div></div>"
        for i, (img, h, p) in enumerate(T["steps"][lang]))
    return f"""<!doctype html><html lang={lang}><head><meta charset=utf-8><title>{t('title')}</title><style>{CSS}</style></head><body>
<h1>{t('title')}</h1><div class=sub>{t('subtitle')}</div>
<div class=row>
<figure><img src='images/overview_start.png' style='height:420px'><figcaption>{t('fig_start')}</figcaption></figure>
<figure><img src='images/overview_finish.png' style='height:340px'><figcaption>{t('fig_finish')}</figcaption></figure>
</div>
<h2>{t('h_parts')}</h2><p>{t('parts_intro')}</p>
<table><tr><th>{t('col_part')}</th><th>{t('col_file')} – {t('col_start')}</th><th>{t('col_start')}</th><th>{t('col_file')} – {t('col_finish')}</th><th>{t('col_finish')}</th></tr>{tr}</table>
<p>{t('hand')}</p><p>{t('total')}</p>
<h3>{t('h_hw')}</h3>{li(t('hw'))}
<h2>{t('h_print')}</h2>{li(t('print'))}
<h2>{t('h_test')}</h2>{li(t('test'))}
<h2 class=pb>{t('h_expl')}</h2>
<div class=row style='align-items:center'><img src='images/exploded_start.png' style='height:620px'>
<ol class=legend>{''.join(f'<li>{x}</li>' for x in t('legend'))}</ol></div>
<h2 class=pb>{t('h_steps')}</h2>{steps}
<h2>{t('h_swap')}</h2>{li(t('swap'))}
<div class=land><h2 style='margin-top:0'>{t('h_sketch')}</h2><img src='design_sketch_v3_{lang}.png' style='display:block;margin:0 auto;max-width:100%;max-height:170mm'></div>
</body></html>"""

if __name__ == "__main__":
    for lang in ("de", "en"):
        open(f"assembly_guide_v3_{lang}.html", "w").write(build(lang))
