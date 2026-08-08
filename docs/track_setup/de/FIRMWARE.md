# ESP32-Firmware

Firmware ist das kleine Programm auf dem ESP32. Sie liest die Lichtschranken, merkt sich den exakten lokalen Zeitpunkt einer Unterbrechung und übermittelt das Ereignis anschließend an den Raspberry Pi. Der Abschnitt ist auch als Vorgabe für eine programmierende Person oder einen KI-Agenten nutzbar.

## Gemeinsame Anforderungen

- ESP-IDF oder Arduino-ESP32; für Eventbetrieb ist ESP-IDF mit FreeRTOS empfehlenswert.
- Ein gemeinsames Firmware-Projekt mit `module_role: start|finish`; keine vier separaten Firmwarevarianten.
- Interrupt-Service-Routine (ISR) schreibt nur Zeitstempel, Sensor-ID und Sequenznummer in eine lockfreie/FreeRTOS-Queue.
- Eine Task verarbeitet Entprellung, Rennzustand, persistente Ereigniswarteschlange und Transport.
- Display (optional) aktualisiert maximal einmal pro Sekunde oder bei Zustandswechsel, nie in der ISR.

## Konfiguration

Die Konfigurationsdatei ist die Stelle, an der ein identisches ESP32-Programm zum Start- oder Zielmodul wird. Ändern Sie sie nicht während eines Rennens und bewahren Sie WLAN-Passwörter nicht in öffentlich geteilten Beispieldateien auf.

```yaml
device_id: start-01
module_role: start
gpio:
  lane_1: 16
  lane_2: 17
sensor:
  active_level: 0
  debounce_us: 15000
network:
  ssid: RacingManager
  server_url: ws://192.168.50.1:8080/hardware/esp32/ws
transport:
  primary: wifi
  rs485_fallback: false
```

Das Zielmodul verwendet `device_id: finish-01` und `module_role: finish`. `active_level` muss nach dem realen Sensor geprüft werden: Health-Ansicht muss „frei“ zeigen, wenn der Strahl frei ist. Beim ADA2167 mit internem Pull-up liegt der Pin bei unterbrochenem Strahl auf LOW, `active_level: 0` ist also korrekt. GPIO 16/17 sind die freien Eingänge auf dem ESP32-Board mit integriertem LCD; auf einem normalen DevKit funktionieren auch 32/33 – siehe [Verdrahtung](WIRING.md).

Auf der Racing-Manager-Seite ist das der Gerätemodus `ESP32_WEBSOCKET_DIRECT` („ESP32 WebSocket Direct Connect“ in der Einstellungs-UI): Der Pi betreibt den WebSocket-Server unter `/hardware/esp32/ws`, die Module verbinden sich als Client dorthin – umgekehrt zum älteren Modus `HARDWARE`, der sich zu einem separaten Raspberry-Pi-Controller verbunden hat. Die ausgelieferte Voreinstellung erwartet 4 Module mit je einem Sensoreingang (`lane-1-start`, `lane-1-finish`, `lane-2-start`, `lane-2-finish`) statt der oben gezeigten 2 Module mit je zwei Spuren (`start-01`/`finish-01`) – beide Aufteilungen sprechen dasselbe Protokoll; die Einstellung `expectedDeviceIds` legt fest, welche `device_id`s der Server akzeptiert. Das Backend implementiert den Handshake `race.arm`/`race.armed`/`race.start`/`race.reset` und `time.sync_request`/`time.sync_response` noch nicht: Es verarbeitet aktuell nur `device.register`, `device.heartbeat` und `sensor.event` und ermittelt die Zeit jeder Spur aus der Differenz der Empfangszeitpunkte von Start- und Ziel-`beam_broken`.

## Lauffähige Referenzimplementierung

Der [ESP32-Sensor-Firmware-Leitfaden](../esp32/ESP32_SENSOR_FIRMWARE_GUIDE.md) enthält vollständige, direkt flashbare
Arduino-Sketche für einen Sensoreingang – einen für ein normales DevKit und einen für das Board mit integriertem
Display, inklusive Board-Einstellungen, benötigter Bibliotheken und Display-Pinplan. Das ist der schnellste Weg, die
Hardware nachzuweisen, und ein guter Ausgangspunkt vor der hier beschriebenen vollständigen ESP-IDF-Umsetzung. Der
Leitfaden liegt nur auf Englisch vor und gilt für beide Sprachfassungen.

## Kernablauf

```text
Boot → Selbsttest Sensoren → WLAN/RS485 verbinden → register → heartbeat
  → race.arm → lokale Sensorereignisse erfassen → race.start → Events senden
  → race.reset
```

Beim Netzverlust bleiben Ereignisse mit `event_id` und `sequence` in einer begrenzten Warteschlange. Nach Wiederverbindung werden sie erneut gesendet, bis der Server `event.ack` liefert. Der Server muss Duplikate anhand von `device_id + boot_id + sequence` verwerfen.

## Sensorlogik

Nur das erste gültige `beam_broken` je `race_id`, `role` und Spur zählt. Ignorieren Sie danach mindestens `debounce_us` und bis zu `race.reset` weitere Flanken derselben Messung. Sensorzustände werden unabhängig vom Rennen als `sensor.status` gemeldet.

## Optionales Display

Zeigen: Modulrolle, WLAN/RS485-Status, Serverkontakt, S1/S2 frei/blockiert, aktive `race_id`, letzter Event und Firmwareversion. Ein Display-Board ist nur dann fest zu verdrahten, wenn sein tatsächlicher Pinplan dokumentiert und mit der Tabelle in [WIRING.md](WIRING.md) abgeglichen wurde.

---

**Navigation:** [← Verdrahtung](WIRING.md) · [Weiter: Protokoll →](PROTOCOL.md) · [English](../en/FIRMWARE.md)

**Gemeinsame Ressourcen (englisch):** [CAD-Paket](../cad_files/README.md) · [ESP32-Sensor-Firmware-Leitfaden](../esp32/ESP32_SENSOR_FIRMWARE_GUIDE.md)

**Alle Themen:** [Projektüberblick](PROJECT.md) · [Materialliste](MATERIALS.md) · [Architektur](ARCHITECTURE.md) · [Verdrahtung](WIRING.md) · **Firmware** · [Protokoll](PROTOCOL.md) · [Aufbau](SETUP.md) · [Tests](TESTING.md) · [ESP-Agentenauftrag](ESP_AGENT_GUIDE.md) · [Racing-Manager-Integration](AGENT_RACING_MANAGER_INTEGRATION.md)
