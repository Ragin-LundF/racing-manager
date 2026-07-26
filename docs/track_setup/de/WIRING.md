# Steckplan und Verdrahtung

Jetzt werden die Bauteile elektrisch verbunden. Arbeiten Sie mit ausgeschalteten Netzteilen, verdrahten Sie zunächst nur einen Sensor und prüfen Sie diesen, bevor Sie weitere anschließen. Ein GPIO ist ein kleiner Signaleingang des ESP32 – er darf ausschließlich ein 3,3-V-Signal erhalten.

## Sicherheitsregeln

> **Achtung – 3,3 V:** ESP32-GPIO sind nicht 5-V-tolerant. Ein Sensor-Ausgang mit 5 V zerstört den Eingang möglicherweise sofort. Vor dem Anschluss mit Multimeter und Datenblatt prüfen.

> **Achtung – Versorgung:** Sensoren und ESP32 dürfen aus demselben 5-V-Netzteil versorgt werden, aber alle logisch verbundenen Geräte brauchen eine gemeinsame Masse. Servos/Solenoids erhalten später eine separate, ausreichend starke Versorgung.

## Referenz-Pinplan

Diese Pins sind für ein normales ESP32 DevKit gewählt. Vorhandene Display-Boards können Display-Pins belegen; dann die Zuordnung in der Firmware-Konfiguration ändern.

| Modul | Funktion | ESP32-Pin | Anschluss |
|---|---|---:|---|
| START | Startlichtschranke Spur 1 (OUT) | GPIO 32 | Sensor-Ausgang nach 3,3-V-Anpassung |
| START | Startlichtschranke Spur 2 (OUT) | GPIO 33 | Sensor-Ausgang nach 3,3-V-Anpassung |
| ZIEL | Ziellichtschranke Spur 1 (OUT) | GPIO 32 | Sensor-Ausgang nach 3,3-V-Anpassung |
| ZIEL | Ziellichtschranke Spur 2 (OUT) | GPIO 33 | Sensor-Ausgang nach 3,3-V-Anpassung |
| beide | RS485 RX | GPIO 16 | RO des MAX3485 |
| beide | RS485 TX | GPIO 17 | DI des MAX3485 |
| beide | RS485 DE + /RE | GPIO 4 | DE und /RE zusammenführen |
| START | Reserve: Servo Spur 1/2 | GPIO 25 / 26 | erst später, nur Steuersignal |

GPIO 32/33 sind ausschließlich Eingänge und daher für Sensoren passend. Interne Pull-ups ersetzen keinen definierten Pegelwandler.

Die Tabelle ist ein **Referenzplan**, kein Zwang: Bei einem ESP32 mit eingebautem Display können einzelne Pins bereits vergeben sein. Dann wählen Sie freie, geeignete Eingänge und tragen die Änderung auch in der Firmware-Konfiguration ein.

## Lichtschranke je Messpunkt

```text
IR-Sender:     VCC ─── 3,3 V oder Hersteller-Nennspannung
               GND ─── GND

IR-Empfänger:  VCC ─── passende Versorgung
               GND ─── gemeinsame GND
               OUT ─── [Pegelwandler/Optokoppler] ─── GPIO 32 oder 33
```

Bei einem Open-Collector-Empfänger ist ein Pull-up nach **3,3 V** zulässig, falls das Datenblatt dies erlaubt. Bei Push-Pull-5-V-Ausgang einen bidirektionalen Pegelwandler oder Optokoppler einsetzen; ein pauschaler Widerstandsteiler wird für Event-Hardware nicht als Referenz empfohlen.

## Optionaler RS485-Bus

```text
ESP32 A                    CAT5e/CAT6                     ESP32 B
GPIO17 ─ DI  MAX3485                                  MAX3485  DI ─ GPIO17
GPIO16 ─ RO  MAX3485                                  MAX3485  RO ─ GPIO16
GPIO4  ─ DE,/RE MAX3485                              MAX3485 DE,/RE ─ GPIO4
             A ═══════════════════════════════════════════ A
             B ═══════════════════════════════════════════ B
            GND ───────────────────────────────────────── GND (Referenz)
```

Je 120 Ohm zwischen A und B am Anfang und Ende des Busses. CAT-Kabel ist hier nur Leitung; nicht an Netzwerk-Switch/PoE anschließen. Verwenden Sie 3,3-V-Transceiver (MAX3485, SP3485 oder gleichwertig), nicht ein ungeschütztes 5-V-MAX485-Board.

---

**Navigation:** [← Architektur](ARCHITECTURE.md) · [Weiter: Firmware →](FIRMWARE.md) · [English](../en/WIRING.md)

**Alle Themen:** [Projektüberblick](PROJECT.md) · [Materialliste](MATERIALS.md) · [Architektur](ARCHITECTURE.md) · **Verdrahtung** · [Firmware](FIRMWARE.md) · [Protokoll](PROTOCOL.md) · [Aufbau](SETUP.md) · [Tests](TESTING.md) · [ESP-Agentenauftrag](ESP_AGENT_GUIDE.md) · [Racing-Manager-Integration](AGENT_RACING_MANAGER_INTEGRATION.md)
