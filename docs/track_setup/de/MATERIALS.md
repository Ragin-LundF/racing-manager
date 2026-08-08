# Materialliste

Dieses Kapitel ist die Stückliste des Referenzaufbaus. Die folgende Tabelle nennt die Teile, die tatsächlich gekauft und
verbaut wurden; die verlinkten Amazon.de-Angebote dokumentieren die genaue Variante. Preise, Händler und Verfügbarkeit
ändern sich häufig – prüfen Sie deshalb immer die Spezifikation und nicht nur den Link.

In diesem Schritt wird nur eingekauft und vorbereitet. Kaufen Sie nicht blind ein: Bei Lichtschranken sind Ausgangspegel
und Ausgangsart entscheidend. Jeder Eintrag nennt den Verwendungszweck und die Eigenschaft, die das Teil geeignet macht.

## Tatsächlich verwendete Teile

| Bauteil | Menge | Verwendetes Produkt | Zweck |
|---|---:|---|---|
| ESP32-Board mit 1,96"-LCD | 2 | [diymore ESP32-Entwicklungsplatine mit 1.96"-LCD, CH340, USB-C (2 Stück)](https://www.amazon.de/dp/B0DWWB63YZ) | Je ein Messmodul am Start und am Ziel. Das eingebaute Display zeigt Spurstatus, WLAN und Serververbindung ohne Notebook. |
| ESP32-Breakout-Board, 30-polig | 3 | [ESP32S Breakout Board, GPIO 1 in 2, für 30-Pin-ESP32 (3 Stück)](https://www.amazon.de/dp/B0F6CLP43C) | Trägerplatine im gedruckten Gehäuse. Jeder GPIO liegt doppelt an – als Stiftleiste und als Schraubklemme –, sodass Sensorleitungen ohne Löten angeschlossen werden. |
| IR-Durchlicht-Lichtschranke | 4 | [Adafruit IR Break Beam Sensor – 3 mm LEDs (ADA2167)](https://www.amazon.de/dp/B01BU6YBWU) | Je ein Sender/Empfänger-Paar pro Messpunkt: Spur 1 und Spur 2, jeweils Start und Ziel. 3,3–5,5 V, Open-Collector-Ausgang, Reaktionszeit < 2 ms. |
| WAGO 221-413 Hebelklemme | 30er-Packung | [WAGO 221-413, 3 Leiter, 0,2–4 mm², transparent](https://www.amazon.de/dp/B07NKCWBST) | 5-V- und GND-Verteilung im jeweiligen Gehäuse: Eine Klemme versorgt Platine, Sender und Empfänger gemeinsam. |
| LT-1 Steckklemme | 38er-Packung | [JOYELEC LT-1 Verbindungsklemmen, 1 Eingang / 1 Ausgang, 0,5–2,5 mm²](https://www.amazon.de/dp/B0G6K9WJ2B) | Inline-Verbinder zum Verlängern einzelner Sensoradern im Kabelkanal des Rahmens. |
| Silikonlitzen-Set, 20 AWG | 1 Satz | [SCHDRA Silikonkabel, 20 AWG / 0,5 mm², 6 Farben, je 3 m](https://www.amazon.de/dp/B0C7T9P8G7) | Verlängerungsleitungen für Sender und Empfänger. Feindrähtige Silikonlitze bleibt flexibel und passt in die gedruckten Kabelkanäle; die sechs Farben halten 5 V, GND und Signal eindeutig auseinander. |

Eine 2er-Packung ESP32-Displayboards deckt den Referenzaufbau mit zwei Modulen (Start und Ziel, je zwei Sensoren) ab.
Die Breakout-Boards kommen als 3er-Packung, eines bleibt als Ersatz.

## Zusätzlich nötig, nicht in der Liste oben

Diese Teile waren bereits vorhanden oder sind nicht an ein bestimmtes Angebot gebunden:

| Bauteil | Menge | Mindestanforderung / Zweck |
|---|---:|---|
| Raspberry Pi | 1 | WLAN-Access-Point, Racing Manager, Web-UI |
| Raspberry-Pi-Netzteil, Gehäuse, SD-Karte | 1 | Zum Pi-Modell passend und ausreichend dimensioniert |
| 5-V-Netzteil Start | 1 | 2 A; später 3–5 A bei Servos |
| 5-V-Netzteil Ziel | 1 | 1–2 A |
| USB-C-Kabel | 2 | Zum Flashen und Versorgen der ESP32-Boards |
| PETG-Filament (oder Druckdienstleister) | ca. 1 kg | Rahmen, Abdeckungen und Elektronikgehäuse, siehe [CAD-Paket](../cad_files/README.md) |

## Gedruckte Mechanikteile

Sensorhalter und Gehäuse werden nicht gekauft, sondern gedruckt. Die parametrischen OpenSCAD-Quellen, Druckhinweise und
Exportskripte liegen im [CAD-Paket](../cad_files/README.md), das für beide Sprachfassungen gilt (nur auf Englisch):

- Ein zweispuriger U-Rahmen je Messpunkt, als Start- (340 mm) und Zielvariante (240 mm), mit verdeckten Kabelkanälen und
  Taschen, die auf den Gehäusemaßen des ADA2167 basieren.
- Einschiebbare Kabelabdeckungen.
- Ein aufklebbares Elektronikgehäuse mit Displayausschnitt, USB-Öffnung, Platinenauflagen und Schiebedeckel – darin sitzt
  das Breakout-Board mit dem aufgesteckten ESP32-Displayboard.

Drucken Sie das Testteil für die Sensortasche zuerst, sobald sich eine sensorbezogene Abmessung ändert.

## Warum hier kein Pegelwandler nötig ist

Frühere Fassungen dieses Dokuments forderten vier Pegelwandler oder Optokoppler. Mit dem ADA2167 ist das **nicht** nötig:
Die weiße Ader des Empfängers ist ein Open-Collector-Ausgang. Sie zieht den Pin nur nach GND und gibt selbst nie eine
Spannung aus; den High-Pegel bestimmt allein der interne 3,3-V-Pull-up des ESP32. Der Pin sieht deshalb nie mehr als
3,3 V, auch wenn der Sensor aus 5 V versorgt wird.

Ein Pegelwandler oder Optokoppler ist nur erforderlich, wenn Sie von dieser Liste abweichen und einen Sensor mit
**Push-Pull-5-V-Ausgang** verwenden oder lange Leitungen zu externen 12/24-V-Sensoren führen. Details:
[Verdrahtung](WIRING.md).

## Für WLAN ohne Internet

| Bauteil                | Menge | Empfehlung                                                                   |
|------------------------|------:|------------------------------------------------------------------------------|
| Keine weitere Hardware |     – | Raspberry Pi als WLAN-AP für die beiden ESP32 und eine Registrierungsstation |
| Optionaler Reiserouter |     1 | Stabilerer AP für Eventbetrieb; LAN zum Pi, lokales WLAN ohne WAN            |

## Optionaler RS485-Fallback

| Bauteil                           | Menge | Hinweis                                                                  |
|-----------------------------------|------:|--------------------------------------------------------------------------|
| MAX3485 / 3,3-V-RS485-Transceiver |     2 | **Nicht** den 5-V-MAX485 direkt an ESP32-GPIO betreiben                  |
| CAT5e/CAT6-Kabel                  |     1 | 10–20 m, nur als Kommunikationskabel verwenden                           |
| 120-Ohm-Abschlusswiderstand       |     2 | Je ein Widerstand zwischen A und B an den Busenden                       |
| 680-Ohm-Bias-Widerstände          |   2–3 | Am Master/Start nach Transceiver-Datenblatt; optional bei fertigem Modul |

RS485 war nicht Teil des Einkaufs oben. Auf den Displayboards sind die früher vorgeschlagenen RS485-Pins teilweise vom
LCD belegt; prüfen Sie vor einer Bestellung die Pin-Tabelle in [Verdrahtung](WIRING.md).

## Spätere Startfreigabe

| Bauteil                        | Menge | Hinweis                                           |
|--------------------------------|------:|---------------------------------------------------|
| Servo oder geeigneter Solenoid |     2 | Eine Sperre pro Spur, mechanisch sicher ausführen |
| Separates 5-V-Netzteil         |     1 | 3–5 A für Servos; Masse mit Start-ESP verbinden   |
| Treiber/MOSFET + Freilaufdiode |     2 | Nur bei Solenoids, nie direkt vom ESP32 speisen   |

## Sensorwahl

Bevorzugt werden **Durchlichtsensoren**: Sender und Empfänger stehen einander gegenüber und das Auto unterbricht den
Strahl. Reflexsensoren wie TCRT5000 werden nicht als Referenzaufbau empfohlen: Fahrzeugfarbe, Sonnenlicht und Abstand
beeinflussen sie deutlich stärker.

Eine Lichtschranke besteht hier immer aus zwei Teilen: Ein kleiner Sender erzeugt unsichtbares Infrarotlicht, der
Empfänger sieht dieses Licht. Beim Durchfahren verdeckt das Auto den Strahl. Das ist zuverlässiger als ein Sensor, der
nur auf ein reflektiertes Signal wartet.

Der ADA2167 erfüllt die für diesen Aufbau entscheidenden Kriterien: Er läuft mit 3,3–5,5 V, sein Ausgang ist Open
Collector und damit an einem 3,3-V-GPIO unbedenklich, er reagiert in unter 2 ms, und seine Nennreichweite von etwa 25 cm
deckt einen zweispurigen Rahmen ab. Wenn Sie einen anderen Sensor einsetzen, prüfen Sie zuerst die Ausgangsart: Ein
Push-Pull-5-V-Ausgang darf niemals ohne Anpassung an einen ESP32-GPIO.

---

**Navigation:** [← Projektüberblick](PROJECT.md) · [Weiter: Architektur →](ARCHITECTURE.md) · [English](../en/MATERIALS.md)

**Gemeinsame Ressourcen (englisch):** [CAD-Paket](../cad_files/README.md) · [ESP32-Sensor-Firmware-Leitfaden](../esp32/ESP32_SENSOR_FIRMWARE_GUIDE.md)

**Alle Themen:** [Projektüberblick](PROJECT.md) ·
**Materialliste** · [Architektur](ARCHITECTURE.md) · [Verdrahtung](WIRING.md) · [Firmware](FIRMWARE.md) · [Protokoll](PROTOCOL.md) · [Aufbau](SETUP.md) · [Tests](TESTING.md) · [ESP-Agentenauftrag](ESP_AGENT_GUIDE.md) · [Racing-Manager-Integration](AGENT_RACING_MANAGER_INTEGRATION.md)
