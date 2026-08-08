# 2-spurige Modellauto-Rennstrecke

Dieses Paket beschreibt eine mobile, nachbaubare Rennstrecke mit zwei parallelen Spuren. Ein Raspberry Pi betreibt **Ragin-LundF/racing-manager**, das lokale WLAN und die Bedienoberfläche. Zwei ESP32-Module erfassen die Lichtschranken: eines am Start, eines am Ziel.

Die Anleitung setzt keine Elektronik- oder Programmiererfahrung voraus. Gehen Sie in der angegebenen Reihenfolge vor und überspringen Sie keine Sicherheits- oder Testschritte. Begriffe wie „GPIO“, „ESP32“ und „Lichtschranke“ werden jeweils im praktischen Zusammenhang erklärt.

## Einstieg

1. [Materialliste](MATERIALS.md) beschaffen und die Sicherheitsabschnitte lesen.
2. [Architektur](ARCHITECTURE.md) und [Verdrahtung](WIRING.md) vor dem Bau festlegen.
3. Rahmen und Gehäuse aus dem [CAD-Paket](../cad_files/README.md) drucken.
4. Die Hardware nach [Aufbau](SETUP.md) montieren.
5. ESP-Firmware mit [Firmware](FIRMWARE.md) und [Protokoll](PROTOCOL.md) implementieren; der
   [ESP32-Sensor-Firmware-Leitfaden](../esp32/ESP32_SENSOR_FIRMWARE_GUIDE.md) enthält einen direkt flashbaren Sketch,
   um die Hardware vorab nachzuweisen.
6. Mit [Tests](TESTING.md) abnehmen.

## Geltungsbereich und Referenzannahmen

- Zwei Spuren, je eine Start- und eine Ziellinie: vier optische Durchfahrtsereignisse.
- Strecke: etwa 10–20 m; WLAN ist Standard, RS485 über ein CAT-Kabel optional.
- ESP32-Module: je ein Board am Start und am Ziel, mit zwei Sensor-Eingängen. Der Referenzaufbau nutzt einen ESP32 mit 1,96"-LCD als lokale Statusanzeige; ein normales DevKit funktioniert ebenfalls.
- Raspberry Pi: zentrale Rennlogik, Datenhaltung, Web-UI und lokaler WLAN-Access-Point.
- WLAN: ausschließlich für die beiden ESP32-Module und **eine Registrierungsstation**, an der Teilnehmer und Fahrzeugdaten erfasst werden; es ist kein öffentliches Teilnehmer-WLAN.
- Keine Startsperre in Version 1. Eine spätere gemeinsame Sperre (Servo/Solenoid) gehört an das Startmodul.

## Dokumente

| Dokument | Zweck |
|---|---|
| [MATERIALS.md](MATERIALS.md) | Einkauf und Auswahlkriterien |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Zuständigkeiten, Netzwerk und Trigger |
| [WIRING.md](WIRING.md) | Steck- und Pinplan |
| [FIRMWARE.md](FIRMWARE.md) | ESP-Implementierung und Konfiguration |
| [PROTOCOL.md](PROTOCOL.md) | Nachrichtenvertrag Raspberry Pi ↔ ESP32 |
| [SETUP.md](SETUP.md) | Mechanischer und elektrischer Aufbau |
| [TESTING.md](TESTING.md) | Inbetriebnahme und Event-Checkliste |
| [ESP_AGENT_GUIDE.md](ESP_AGENT_GUIDE.md) | Arbeitsauftrag für einen Firmware-Agenten |
| [AGENT_RACING_MANAGER_INTEGRATION.md](AGENT_RACING_MANAGER_INTEGRATION.md) | Arbeitsauftrag für die zentrale Integration |

## Gemeinsame Ressourcen

Diese beiden Pakete sind sprachneutral und gelten für beide Fassungen. Sie liegen nur auf Englisch vor.

| Paket | Zweck |
|---|---|
| [`../cad_files/`](../cad_files/README.md) | OpenSCAD-Quellen der Lichtschrankentore: U-Rahmen, Kabelabdeckungen, Elektronikgehäuse, Exportskripte und Druckhinweise |
| [`../esp32/ESP32_SENSOR_FIRMWARE_GUIDE.md`](../esp32/ESP32_SENSOR_FIRMWARE_GUIDE.md) | Praxisleitfaden für Arduino: Verdrahtung des ADA2167, Board- und Bibliothekseinrichtung sowie zwei vollständige Sketche |

## Wichtiger Genauigkeitshinweis

Zwei getrennte ESP32 haben ohne Synchronisation **keine gemeinsame Uhr**. Reine WLAN-Zeitstempel sind für eine sichtbare Rennanzeige brauchbar, aber nicht als garantierte Sub-Millisekunden-Zeitmessung auszugeben. Dieses Design verwendet deshalb eine synchronisierte Rennreferenz und kennzeichnet Messqualität. Für belastbare, sehr genaue Ergebniszeiten ist RS485 als Kabelweg oder eine gemeinsame Freigabe der Startmechanik die bevorzugte Betriebsart. Details: [Architektur](ARCHITECTURE.md#zeitbasis-und-genauigkeit).

Die englische Fassung liegt unter [`../en/PROJECT.md`](../en/PROJECT.md).

---

**Navigation:** Start · [Weiter: Materialliste →](MATERIALS.md) · [English](../en/PROJECT.md)

**Alle Themen:** [Projektüberblick](PROJECT.md) · [Materialliste](MATERIALS.md) · [Architektur](ARCHITECTURE.md) · [Verdrahtung](WIRING.md) · [Firmware](FIRMWARE.md) · [Protokoll](PROTOCOL.md) · [Aufbau](SETUP.md) · [Tests](TESTING.md) · [ESP-Agentenauftrag](ESP_AGENT_GUIDE.md) · [Racing-Manager-Integration](AGENT_RACING_MANAGER_INTEGRATION.md)
