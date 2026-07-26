# Systemarchitektur und Triggerplan

Dieser Abschnitt erklärt, welches Gerät welche Aufgabe übernimmt. Das ist wichtig, weil der Raspberry Pi die Rennen organisiert, während die ESP32 die schnellen Sensorimpulse direkt vor Ort erfassen. So bleibt die Anlage auch dann übersichtlich, wenn später eine Startsperre ergänzt wird.

## Übersicht

```text
 Registrierungsstation ─── WLAN ohne Internet ── Raspberry Pi
 (Teilnehmer + Fahrzeuge)                         Racing Manager + API + UI
 Rennleiter-Notebook ───── LAN oder lokales WLAN ─┤
 Rennleiter-Notebook ───── HDMI/USB-C/DisplayPort ─ Beamer oder externer TV
                                                    │ WebSocket (primär)
                               ┌────────────────────┴────────────────────┐
                               │                                         │
                         ESP32 START                               ESP32 ZIEL
                         S1 Start, S2 Start                        S1 Ziel, S2 Ziel
                               ╰──────── optional RS485 ────────────────╯
```

Der Raspberry Pi ist kein zeitkritischer Sensor-Controller. Er verwaltet Rennen, Teilnehmer, UI, Speicherung und den Verbindungszustand. Jedes ESP32-Modul entprellt seine lokalen Sensoren per Hardware-Interrupt und speichert Ereignisse auch bei einer vorübergehenden Netzstörung.

Die Registrierungsstation ist ein einzelnes Notebook oder Tablet am Anmeldepunkt. Dort werden Namen und Fahrzeugdaten vor dem Rennen im Racing Manager erfasst. Das lokale WLAN ist daher ein internes Arbeitsnetz und kein Netz für Besucher. Der Rennleiter öffnet die Zuschaueransicht auf seinem Notebook. **Beamer oder externer TV werden ausschließlich direkt an dieses Notebook angeschlossen** (zum Beispiel über HDMI, USB-C/DisplayPort-Adapter oder eine Dockingstation); der Raspberry Pi liefert kein eigenes Bildsignal für die Zuschaueranzeige.

## Zuordnung

| Messpunkt | Besitzer | Sensor-ID | Ereignis |
|---|---|---|---|
| Spur 1 Start | ESP32 START | `start.lane_1` | `beam_broken` |
| Spur 2 Start | ESP32 START | `start.lane_2` | `beam_broken` |
| Spur 1 Ziel | ESP32 ZIEL | `finish.lane_1` | `beam_broken` |
| Spur 2 Ziel | ESP32 ZIEL | `finish.lane_2` | `beam_broken` |

## Zustände und Trigger

```text
IDLE → HEALTHY → ARMED → RUNNING → FINISHED → IDLE
                 │          │
             Sensorfehler   └─ Start-/Zielereignisse
```

1. Der Racing Manager prüft Heartbeats, Sensoren frei und passende Konfiguration.
2. Der Rennleiter wählt aktive Spuren und sendet `race.arm` mit `race_id`.
3. Beide Module bestätigen `race.armed`; gesperrte oder bereits blockierte Sensoren verhindern den Start.
4. `race.start` startet das Rennen. Mit späterer Sperre öffnet START die Sperren gleichzeitig und sendet die Startreferenz an ZIEL.
5. Die Start-Lichtschranke liefert pro aktiver Spur das tatsächliche Startdurchfahrts-Ereignis. Die Ziellinie beendet diese Spur.
6. Der Pi bildet Ergebnis, Status und Zuschaueransicht. Nach `race.reset` löschen Module nur den flüchtigen Rennzustand.

## Zeitbasis und Genauigkeit

- Jeder ESP32 stempelt Interrupts mit seiner lokalen Mikrosekunden-Uhr; im Interrupt erfolgen keine Displays, JSON-Serialisierung oder WLAN-Aufrufe.
- Der Pi führt vor `race.arm` mehrere Zeit-Synchronisationsrunden durch (`time.sync_request`/`time.sync_response`) und speichert Offset und geschätzte Unsicherheit je Modul.
- **WLAN-Modus:** als Zielwert für Modellauto-Veranstaltungen: Ergebnis mit Qualitätsflag `estimated` und angegebener Synchronisationsunsicherheit. Nicht für garantierte Sub-Millisekunden-Wertungen verwenden.
- **RS485-Modus:** bevorzugt bei Wertungen; der Bus transportiert die gleichen Nachrichten robuster. Die gemeinsame Startfreigabe (`race.start_reference`) erzeugt eine besonders konsistente Rennreferenz.
- Für sehr kurze oder rekordrelevante Rennen: Zeiten ab der gemeinsamen Freigabe messen; Start-Lichtschranken als Fehlstart-/Durchfahrtsprüfung verwenden. Eine echte Start-zu-Ziel-Zeit über getrennte Uhren bleibt nur so genau wie deren Synchronisation.

## Netzwerk

Der Raspberry Pi stellt eine WPA2-geschützte SSID bereit, zum Beispiel `RacingManager`, ohne Internetzugang. Verbinden Sie damit nur `start-01`, `finish-01` und die Registrierungsstation. Der Pi erhält eine feste Adresse, z. B. `192.168.50.1`; ESP32 erhalten reservierte Adressen oder werden über Geräte-ID erkannt. Für Eventbetrieb ist ein dedizierter Reiserouter als AP stabiler; der Pi bleibt per Ethernet verbunden.

---

**Navigation:** [← Materialliste](MATERIALS.md) · [Weiter: Verdrahtung →](WIRING.md) · [English](../en/ARCHITECTURE.md)

**Alle Themen:** [Projektüberblick](PROJECT.md) · [Materialliste](MATERIALS.md) · **Architektur** · [Verdrahtung](WIRING.md) · [Firmware](FIRMWARE.md) · [Protokoll](PROTOCOL.md) · [Aufbau](SETUP.md) · [Tests](TESTING.md) · [ESP-Agentenauftrag](ESP_AGENT_GUIDE.md) · [Racing-Manager-Integration](AGENT_RACING_MANAGER_INTEGRATION.md)
