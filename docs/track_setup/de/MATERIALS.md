# Materialliste

Preise und konkrete Händlerangebote ändern sich häufig; diese Liste nennt daher Spezifikation und Menge statt flüchtiger
Produktlinks.

In diesem Schritt wird nur eingekauft und vorbereitet. Kaufen Sie nicht blind ein: Besonders bei Lichtschranken ist der
Ausgangspegel entscheidend. Die Liste beschreibt, wofür jedes Teil gebraucht wird und welche Eigenschaften es mindestens
haben muss.

## Pflichtmaterial für eine 2-spurige Strecke

| Bauteil                             |   Menge | Mindestanforderung / Zweck                                      |
|-------------------------------------|--------:|-----------------------------------------------------------------|
| Raspberry Pi                        |       1 | Bereits vorhanden; WLAN, Racing Manager, Web-UI                 |
| Raspberry-Pi-Netzteil               |       1 | Zum Pi-Modell passend und ausreichend dimensioniert             |
| ESP32 DevKit                        |       2 | Klassischer ESP32, USB, mindestens 6 freie GPIO; Start und Ziel |
| IR-Durchlicht-Lichtschranke         | 4 Paare | Sender + Empfänger, für zwei Spuren an Start und Ziel           |
| 3,3-V-Pegelwandler oder Optokoppler |       4 | **Erforderlich**, falls Empfänger-Ausgang 5 V liefert           |
| 5-V-Netzteil Start                  |       1 | 2 A; später 3–5 A bei Servos                                    |
| 5-V-Netzteil Ziel                   |       1 | 1–2 A                                                           |
| USB-Kabel                           |       2 | Passend zu den ESP32                                            |
| Gehäuse                             |       2 | Zugentlastung, Spritz-/Berührungsschutz                         |
| Litze, Klemmen, Dupont-Leitungen    |  1 Satz | Kurze interne Verbindungen                                      |
| Sensorhalter                        |       8 | Starr und justierbar, je Sender/Empfänger                       |
| Raspberry-Pi-Gehäuse/SD-Karte       |       1 | Falls noch nicht vorhanden                                      |

- [diymore 2PCS für ESP32 Entwicklungsplatine mit 1.96inch LCD Display 2.4 GHz WLAN WiFi Bluetooth BLE MCU CH340 Chip für ESP32 Nodemcu USB C](https://www.amazon.de/dp/B0DWWB63YZ)
- [Diymore 2 Stück für ESP32 und Terminal Adapter, für ESP32 Dev Kit C Development Board NodeMcu Entwicklungsplatine 2,4 GHz Dual Core WLAN WiFi Bluetooth CP2102 Chip](https://www.amazon.de/dp/B0DK1XLB1K)
- [Adafruit IR Break Beam Sensor - 3mm LEDs (ADA2167)](https://www.amazon.de/dp/B01BU6YBWU)
- [PENGLIN 5 Stück PC817 2 Kanal Optokoppler,Spannungsisolationsplatine, | Spannungsgesteuertes Anschlussmodul, Eingang 3.6–24V, Ausgang 3.6–30V für Arduino und Industrielle Steuerung](https://www.amazon.de/dp/B0H2TPW6J8)

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

Kaufen Sie idealerweise Sensoren mit 3,3-V-kompatiblem, digitalem Ausgang. Bei einem 5-V-Sensor sind Versorgung und
Logik getrennt zu betrachten: Ein 5-V-Ausgang darf niemals an einen ESP32-GPIO. Ein Pegelwandler, Optokoppler oder ein
korrekt berechneter Spannungsteiler ist dann Pflicht.

---

**Navigation:** [← Projektüberblick](PROJECT.md) · [Weiter: Architektur →](ARCHITECTURE.md) · [English](../en/MATERIALS.md)

**Alle Themen:** [Projektüberblick](PROJECT.md) ·
**Materialliste** · [Architektur](ARCHITECTURE.md) · [Verdrahtung](WIRING.md) · [Firmware](FIRMWARE.md) · [Protokoll](PROTOCOL.md) · [Aufbau](SETUP.md) · [Tests](TESTING.md) · [ESP-Agentenauftrag](ESP_AGENT_GUIDE.md) · [Racing-Manager-Integration](AGENT_RACING_MANAGER_INTEGRATION.md)
