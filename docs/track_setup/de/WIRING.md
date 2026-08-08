# Steckplan und Verdrahtung

Jetzt werden die Bauteile elektrisch verbunden. Arbeiten Sie mit ausgeschalteten Netzteilen, verdrahten Sie zunächst nur einen Sensor und prüfen Sie diesen, bevor Sie weitere anschließen. Ein GPIO ist ein kleiner Signaleingang des ESP32 – er darf ausschließlich ein 3,3-V-Signal erhalten.

## Sicherheitsregeln

> **Achtung – 3,3 V:** ESP32-GPIO sind nicht 5-V-tolerant. Ein *Push-Pull*-Sensorausgang mit 5 V zerstört den Eingang möglicherweise sofort. Vor dem Anschluss mit Multimeter und Datenblatt prüfen.

> **Achtung – Versorgung:** Sensoren und ESP32 dürfen aus demselben 5-V-Netzteil versorgt werden, aber alle logisch verbundenen Geräte brauchen eine gemeinsame Masse. Servos/Solenoids erhalten später eine separate, ausreichend starke Versorgung.

## Der verwendete Sensor: ADA2167

Der [Adafruit ADA2167](https://www.amazon.de/dp/B01BU6YBWU) hat je Teil drei Adern:

| Teil | Aderfarbe | Funktion | Anschluss |
|---|---|---|---|
| IR-Sender | rot | Versorgung | 5 V / VIN |
| IR-Sender | schwarz | Masse | GND |
| IR-Empfänger | rot | Versorgung | 5 V / VIN |
| IR-Empfänger | schwarz | Masse | GND |
| IR-Empfänger | weiß | Open-Collector-Signal | Sensor-GPIO des ESP32 |

Die weiße Ader ist ein **Open-Collector-Ausgang**: Sie zieht den GPIO nur nach GND und gibt selbst nie eine Spannung
aus. Den High-Pegel liefert der interne 3,3-V-Pull-up des ESP32 (`INPUT_PULLUP`), der Pin sieht also nie mehr als
3,3 V – auch wenn der Sensor aus 5 V versorgt wird. **Ein Pegelwandler oder Optokoppler ist für diesen Sensor bei
kurzen Leitungen im Rahmen nicht nötig.**

Setzen Sie keinen externen Pull-up nach 5 V auf die weiße Ader – das würde 5 V an den GPIO legen.

## Verteilung innerhalb eines Moduls

Die Versorgung wird mit den [WAGO 221-413](https://www.amazon.de/dp/B07NKCWBST) Hebelklemmen für drei Leiter verteilt,
je eine für 5 V und eine für GND pro Messmodul. Einzelne Sensoradern werden mit
[20-AWG-Silikonlitze](https://www.amazon.de/dp/B0C7T9P8G7) verlängert und mit
[LT-1-Steckklemmen](https://www.amazon.de/dp/B0G6K9WJ2B) inline verbunden.

```text
5-V-WAGO:  ESP32 5V/VIN  ─┬─ Sender rot
                          └─ Empfänger rot

GND-WAGO:  ESP32 GND     ─┬─ Sender schwarz
                          └─ Empfänger schwarz

Signal:    Empfänger weiß ─── Sensor-GPIO (interner Pull-up aktiv)
```

Bei zwei Spuren je Modul verwenden Sie eine 5-V- und eine GND-WAGO pro Spur oder eine kurze Kette davon. Die weiße Ader
jeder Spur geht auf einen eigenen GPIO – die beiden Signaladern niemals zusammenführen.

Das ESP32-Board wird auf das [30-polige Breakout-Board](https://www.amazon.de/dp/B0F6CLP43C) gesteckt, dessen
Schraubklemmen die Sensorleitungen ohne Löten aufnehmen. Auf diese Baugruppe ist das gedruckte
[Elektronikgehäuse](../cad_files/README.md) ausgelegt.

## Referenz-Pinplan

Der Pinplan hängt vom Board ab. Der Referenzaufbau nutzt den ESP32 mit integriertem 1,96"-LCD; das Display belegt dort
mehrere Pins, die frühere Fassungen dieses Dokuments den Sensoren zugewiesen hatten.

| Modul | Funktion | Normales ESP32 DevKit | Board mit integriertem LCD |
|---|---|---:|---:|
| START | Startlichtschranke Spur 1 (weiße Ader) | GPIO 32 | GPIO 16 |
| START | Startlichtschranke Spur 2 (weiße Ader) | GPIO 33 | GPIO 17 |
| ZIEL | Ziellichtschranke Spur 1 (weiße Ader) | GPIO 32 | GPIO 16 |
| ZIEL | Ziellichtschranke Spur 2 (weiße Ader) | GPIO 33 | GPIO 17 |
| beide | RS485 RX | GPIO 16 | GPIO 25 |
| beide | RS485 TX | GPIO 17 | GPIO 26 |
| beide | RS485 DE + /RE | GPIO 4 | GPIO 27 |
| START | Reserve: Servo Spur 1/2 | GPIO 25 / 26 | GPIO 13 / 14 |

Auf dem Displayboard belegt das LCD MOSI 23, SCLK 18, CS 15, DC 2, RST 4 und Hintergrundbeleuchtung 32. GPIO 32, 4 und 2
stehen dort also weder für Sensoren noch für RS485 zur Verfügung – deshalb wandern die Sensoreingänge auf GPIO 16 und
17. Die Firmware-Beispiele im [ESP32-Sensor-Firmware-Leitfaden](../esp32/ESP32_SENSOR_FIRMWARE_GUIDE.md) (englisch)
verwenden GPIO 16 für den dort dokumentierten einzelnen Sensoreingang.

Auf einem normalen DevKit sind GPIO 32/33 weiterhin als Eingänge nutzbar und dort die empfohlene Wahl.

Die Tabelle ist ein **Referenzplan**, kein Zwang: Prüfen Sie vor dem Verdrahten den Aufdruck und den Display-Pinplan des
tatsächlich gelieferten Boards und übernehmen Sie jede Abweichung auch in die Firmware-Konfiguration
([Firmware](FIRMWARE.md)).

## Wann doch eine Anpassung nötig ist

Wenn Sie den ADA2167 durch einen Sensor mit Push-Pull-5-V-Ausgang ersetzen oder lange Leitungen zu externen
12/24-V-Sensoren führen, setzen Sie einen bidirektionalen Pegelwandler, einen Optokoppler oder einen galvanisch
getrennten Digitaleingang ein. Ein pauschaler Widerstandsteiler wird für Event-Hardware nicht als Referenz empfohlen.

## Optionaler RS485-Bus

```text
ESP32 A                    CAT5e/CAT6                     ESP32 B
TX-Pin ─ DI  MAX3485                                  MAX3485  DI ─ TX-Pin
RX-Pin ─ RO  MAX3485                                  MAX3485  RO ─ RX-Pin
DE-Pin ─ DE,/RE MAX3485                              MAX3485 DE,/RE ─ DE-Pin
             A ═══════════════════════════════════════════ A
             B ═══════════════════════════════════════════ B
            GND ───────────────────────────────────────── GND (Referenz)
```

Je 120 Ohm zwischen A und B am Anfang und Ende des Busses. CAT-Kabel ist hier nur Leitung; nicht an Netzwerk-Switch/PoE anschließen. Verwenden Sie 3,3-V-Transceiver (MAX3485, SP3485 oder gleichwertig), nicht ein ungeschütztes 5-V-MAX485-Board.

RS485 war nicht Teil des Referenzeinkaufs. Auf dem Displayboard konkurriert es zusätzlich mit den LCD-Pins; prüfen Sie
deshalb die Tabelle oben, bevor Sie Transceiver bestellen.

---

**Navigation:** [← Architektur](ARCHITECTURE.md) · [Weiter: Firmware →](FIRMWARE.md) · [English](../en/WIRING.md)

**Gemeinsame Ressourcen (englisch):** [CAD-Paket](../cad_files/README.md) · [ESP32-Sensor-Firmware-Leitfaden](../esp32/ESP32_SENSOR_FIRMWARE_GUIDE.md)

**Alle Themen:** [Projektüberblick](PROJECT.md) · [Materialliste](MATERIALS.md) · [Architektur](ARCHITECTURE.md) · **Verdrahtung** · [Firmware](FIRMWARE.md) · [Protokoll](PROTOCOL.md) · [Aufbau](SETUP.md) · [Tests](TESTING.md) · [ESP-Agentenauftrag](ESP_AGENT_GUIDE.md) · [Racing-Manager-Integration](AGENT_RACING_MANAGER_INTEGRATION.md)
