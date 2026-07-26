# Agentenauftrag: Integration in Ragin-LundF/racing-manager

Diese Datei ist die Übergabe an die Person oder den Agenten, der die Hardware in die bestehende Anwendung integriert. Sie ergänzt die Anwendungslogik, ohne die bereits funktionierende Teilnehmer- und Rennverwaltung zu ersetzen.

## Auftrag und Grenzen

Implementiere die zentrale Hardware-Anbindung auf dem Raspberry Pi in das bestehende Projekt. Prüfe zuerst Architektur, Framework, Authentifizierung, Echtzeitkanal und Datenmodell des Repositorys. Nutze vorhandene Muster; erfinde keinen zweiten Webserver oder parallelen State Store.

Das Repository war bei Erstellung dieses Dokuments nicht lokal verfügbar. Daher sind Dateinamen und Framework-Details bewusst nicht vorgegeben. Der verbindliche Gerätevertrag steht in [PROTOCOL.md](PROTOCOL.md).

## Funktionsumfang

- WebSocket-Endpunkt für ESP32-Geräte mit Geräte-Registrierung, Heartbeats und Reconnect.
- Modul-Registry: `device_id`, Rolle, Boot-ID, Firmware, Health, Transport, letzte Verbindung, Sensorstatus.
- Rennadapter: `race.arm`, `race.start`, `race.reset`; nur valide Ereignisse aktiver Spuren akzeptieren.
- Pro Spur Rohereignis Start/Ziel, berechnete Zeit, Zeitunsicherheit und Ergebnisqualität speichern.
- Deduplizierung über `device_id + boot_id + sequence`; ACK erst nach dauerhafter Annahme.
- Zeit-Synchronisationsservice und sichtbare Warnung bei überschrittener Unsicherheit.
- Bedien-/Health-UI und Zuschaueransicht über bereits vorhandenen Live-Update-Mechanismus versorgen.

## Reihenfolge

1. Bestehende Domain-Modelle für Rennen, Spuren, Resultate und Live-Events identifizieren.
2. Hardware-Port als abgegrenzte Schnittstelle erstellen; Transport-Handler darf keine Rennregeln enthalten.
3. Zustandsübergänge und Persistenz transaktional implementieren.
4. API/UI nur auf den zentralen Hardware-Status zugreifen lassen.
5. Simulator/Fake-ESP für lokale Tests hinzufügen.

## Sicherheits- und Qualitätsregeln

- Ein unbekanntes Gerät darf kein Rennen steuern; Geräte über Konfiguration oder Pairing zulassen.
- Nachrichten-Größe, JSON-Validierung, Rate Limits und Verbindungsabbrüche behandeln.
- Ein ESP-Neustart oder widersprüchliches Ereignis macht das betroffene Rennen sichtbar ungültig/prüfbedürftig, nicht stillschweigend erfolgreich.
- Hardwarezeit und Serverzeit nicht vermischen: Rohstempel, Sync-Offset, Unsicherheit und berechnete Zeit separat speichern.

## Abnahme

Automatisierte Tests für Registrierung, fehlende Heartbeats, ACK/Deduplizierung, ungültige Ereignisreihenfolge, ein- und zweispurige Rennen sowie Wiederverbindung. Dokumentiere den tatsächlichen Einstiegspunkt und die Konfiguration in der Projekt-Dokumentation.

---

**Navigation:** [← ESP-Agentenauftrag](ESP_AGENT_GUIDE.md) · [Zurück zum Start](PROJECT.md) · [English](../en/AGENT_RACING_MANAGER_INTEGRATION.md)

**Alle Themen:** [Projektüberblick](PROJECT.md) · [Materialliste](MATERIALS.md) · [Architektur](ARCHITECTURE.md) · [Verdrahtung](WIRING.md) · [Firmware](FIRMWARE.md) · [Protokoll](PROTOCOL.md) · [Aufbau](SETUP.md) · [Tests](TESTING.md) · [ESP-Agentenauftrag](ESP_AGENT_GUIDE.md) · **Racing-Manager-Integration**
