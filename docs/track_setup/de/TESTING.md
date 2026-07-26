# Tests und Event-Checkliste

Die Tests zeigen nicht nur, ob „irgendetwas passiert“, sondern ob jeder Sensor der richtigen Spur zugeordnet ist und eine Störung sichtbar wird. Arbeiten Sie die Tabelle vor dem ersten Einsatz und vor jeder Veranstaltung vollständig ab.

## Abnahmetests

| Test | Soll-Ergebnis |
|---|---|
| Sensor einzeln verdecken | Genau ein passender Health-Wechsel und Event im Diagnosetest |
| Sensor 30 s frei | Kein falsches Ereignis |
| Beide Startsensoren | Lanes bleiben getrennt |
| Rennen Spur 1 allein | Nur Spur 1 startet/endet; Spur 2 bleibt ungewertet |
| Rennen zwei Spuren | Jede Spur erhält genau einen Start und ein Ziel |
| WLAN trennen | ESP puffert, verbindet neu, Server dedupliziert |
| RS485 aktivieren | Gleiche Protokollsemantik, Fehlerzähler sichtbar |
| Neustart eines ESP während Rennen | Rennen wird im UI ungültig/unterbrochen, nicht stillschweigend gewertet |

## Messqualität prüfen

Führen Sie 20 Durchläufe einer kontrollierten Referenz durch und protokollieren Sie Laufzeit, `sync_uncertainty_us`, Transport und Ausreißer. Definieren Sie vor dem Event eine akzeptierte Toleranz. Wenn diese nicht erreicht wird, RS485 verwenden oder ab gemeinsamer Startfreigabe werten.

## Checkliste unmittelbar vor dem Event

- [ ] Pi, Netzteile, Sensorhalter und Reservekabel geprüft
- [ ] Lokales WLAN und alle Bediengeräte verbunden
- [ ] Beide Module online, Firmwareversionen passend
- [ ] Alle Sensoren frei und korrekt beschriftet
- [ ] Testlauf mit einer und mit zwei Spuren bestanden
- [ ] Zeitqualitätswarnungen im UI sichtbar
- [ ] Ergebnisexport/Datensicherung getestet

## Fehlerbilder

| Symptom | Wahrscheinliche Ursache | Aktion |
|---|---|---|
| Immer `blocked` | Strahl falsch ausgerichtet / falscher Pegel | Optik und `active_level` prüfen |
| Zufallsevents | Sonne, lose Leitung, keine Entprellung | Blende, Zugentlastung, Filter prüfen |
| ESP offline | AP-Reichweite/Stromversorgung | Nähe, Netzteil, Antenne und Logs prüfen |
| Unplausible Zeiten | Uhrunsicherheit oder falsche Reihenfolge | Sync-Qualität prüfen, Rennen ungültig machen |

**Nächster Schritt für Entwickler:** [ESP_AGENT_GUIDE.md](ESP_AGENT_GUIDE.md). Für die Einbindung in Racing Manager folgt danach [AGENT_RACING_MANAGER_INTEGRATION.md](AGENT_RACING_MANAGER_INTEGRATION.md).

---

**Navigation:** [← Aufbau](SETUP.md) · [Weiter: ESP-Agentenauftrag →](ESP_AGENT_GUIDE.md) · [English](../en/TESTING.md)

**Alle Themen:** [Projektüberblick](PROJECT.md) · [Materialliste](MATERIALS.md) · [Architektur](ARCHITECTURE.md) · [Verdrahtung](WIRING.md) · [Firmware](FIRMWARE.md) · [Protokoll](PROTOCOL.md) · [Aufbau](SETUP.md) · **Tests** · [ESP-Agentenauftrag](ESP_AGENT_GUIDE.md) · [Racing-Manager-Integration](AGENT_RACING_MANAGER_INTEGRATION.md)
