# Aufbau und Konfiguration

In diesem Schritt wird aus den Einzelteilen eine Strecke. Bauen Sie zuerst mechanisch sauber und verdrahten Sie erst danach. Eine stabile Sensorhalterung ist für zuverlässige Zeiten meist wichtiger als eine kompliziertere Software.

## Mechanischer Aufbau

1. Markieren Sie Start- und Ziellinie rechtwinklig zu jeder Spur.
2. Montieren Sie Sender und Empfänger einer Lichtschranke gegenüber, auf gleicher Höhe und außerhalb der Fahrzeugkontaktzone.
3. Jeder Sensor erhält eine eigene Halterung; vermeiden Sie eine gemeinsame, vibrierende Leiste.
4. Schützen Sie die Optik vor direkter Sonne mit kurzen Blenden. Prüfen Sie bei der realen Veranstaltungsbeleuchtung.
5. Setzen Sie START-ESP in ein Gehäuse nahe Startsensoren und ZIEL-ESP nahe Zielsensoren. Sensorleitungen bleiben kurz.

## Elektrischer Aufbau

1. Noch ohne ESP32 prüfen: Versorgungsspannung und Ausgangspegel jedes Empfängers messen.
2. Pegelwandler/Optokoppler einbauen, dann GPIO anschließen.
3. Ein Modul nach dem anderen starten. Im Health-Status müssen beide Strahlen als `clear` erscheinen.
4. Erst nach erfolgreichem Sensortest WLAN und optional RS485 aktivieren.

## Raspberry Pi als internes lokales WLAN

Der Pi bekommt eine feste WLAN-IP, z. B. `192.168.50.1`, DHCP für `192.168.50.100–.199`, WPA2-Passwort und einen lokalen DNS-Namen wie `racing.local`. Dieses WLAN dient den ESP32-Modulen und einer einzelnen Registrierungsstation, nicht Besuchern. Die genaue Einrichtung hängt vom Pi-OS-Release und NetworkManager ab; dokumentieren Sie die gewählte SSID, das Passwort und die Reservierungen nicht im Firmware-Repository.

Für die Veranstaltung: Pi und AP/Router gegen Stromausfall sichern, keine automatische WLAN-Roaming- oder Internetabhängigkeit einplanen, und ein Notebook vorab mit dem lokalen Netz testen.

## Reihenfolge vor einem Rennen

1. Pi und Racing Manager starten.
2. START und ZIEL einschalten; beide registrieren sich und melden Health.
3. Sensoren ausrichten, bis alle vier `clear` melden.
4. Aktive Spuren wählen, `race.arm` senden und Bestätigung beider Module abwarten.
5. Rennen starten; die Zuschaueransicht bleibt rein lesend.

Die Zuschaueransicht wird auf dem Rennleiter-Notebook geöffnet. Verbinden Sie anschließend den Beamer oder TV mit diesem Notebook und wählen Sie dort die gewünschte Anzeige (duplizieren oder erweitern). Der Raspberry Pi ist nur Server und Netzwerkzentrale; schließen Sie den Beamer nicht als Voraussetzung an ihn an.

---

**Navigation:** [← Protokoll](PROTOCOL.md) · [Weiter: Tests →](TESTING.md) · [English](../en/SETUP.md)

**Alle Themen:** [Projektüberblick](PROJECT.md) · [Materialliste](MATERIALS.md) · [Architektur](ARCHITECTURE.md) · [Verdrahtung](WIRING.md) · [Firmware](FIRMWARE.md) · [Protokoll](PROTOCOL.md) · **Aufbau** · [Tests](TESTING.md) · [ESP-Agentenauftrag](ESP_AGENT_GUIDE.md) · [Racing-Manager-Integration](AGENT_RACING_MANAGER_INTEGRATION.md)
