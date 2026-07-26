# Agentenauftrag: ESP32-Firmware

Diese Datei kann unverändert als Arbeitsauftrag an einen KI-Agenten oder eine programmierende Person gegeben werden. Sie setzt die vorherigen Kapitel als technische Spezifikation voraus.

## Ziel

Implementiere eine gemeinsame Firmware für `start-01` und `finish-01` gemäß [PROTOCOL.md](PROTOCOL.md), [FIRMWARE.md](FIRMWARE.md) und [WIRING.md](WIRING.md). Kein GPIO darf fest im Code versteckt sein; Rollen, Pins, Sensorpolarität und Serveradresse sind konfigurierbar.

## Nicht verhandelbare Anforderungen

- ISR minimal halten: nur `esp_timer_get_time()`, Sensor-ID, Sequenz in Queue; keine Netzwerk-/Display-Calls.
- Aktive Rennzustände strikt durchsetzen; je Sensor/Rennen nur erstes valides Ereignis.
- Heartbeat jede Sekunde, Registrierung nach jeder Verbindung, Event-ACK und persistente Wiederholung.
- Offline-Puffer mit begrenzter Größe, sichtbarem Überlauf und Duplikatschutz (`boot_id`, `sequence`).
- Zeit-Sync implementieren und `sync_uncertainty_us` ausgeben; bei schlechter Qualität keine falsche Präzision vortäuschen.
- WLAN primär, RS485 hinter einer austauschbaren Transport-Schnittstelle; im ersten Inkrement darf RS485 als klar gekennzeichnetes Feature-Flag deaktiviert sein.

## Lieferumfang

1. Build- und Flash-Anleitung.
2. Beispielkonfigurationen für START und ZIEL.
3. Unit-Tests für Zustandsautomat, Entprellung und Event-Deduplizierung.
4. Hardware-in-the-loop-Testskript oder dokumentierter manueller Test.
5. Kurze README mit unterstützten Boardvarianten und Pin-Konflikten.

## Akzeptanz

Beweise mit Logs/Tests: zwei parallele Spuren, einspuriges Rennen, WLAN-Reconnect ohne verloren gewertetes Ereignis, Sensorfehler vor `race.arm` und Neustart während eines Rennens. Änderungen am Protokoll nur versioniert und gemeinsam mit dem zentralen Integrationsvertrag.

---

**Navigation:** [← Tests](TESTING.md) · [Weiter: Racing-Manager-Integration →](AGENT_RACING_MANAGER_INTEGRATION.md) · [English](../en/ESP_AGENT_GUIDE.md)

**Alle Themen:** [Projektüberblick](PROJECT.md) · [Materialliste](MATERIALS.md) · [Architektur](ARCHITECTURE.md) · [Verdrahtung](WIRING.md) · [Firmware](FIRMWARE.md) · [Protokoll](PROTOCOL.md) · [Aufbau](SETUP.md) · [Tests](TESTING.md) · **ESP-Agentenauftrag** · [Racing-Manager-Integration](AGENT_RACING_MANAGER_INTEGRATION.md)
