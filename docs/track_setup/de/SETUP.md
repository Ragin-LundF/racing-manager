# Aufbau und Konfiguration

In diesem Schritt wird aus den Einzelteilen eine Strecke. Bauen Sie zuerst mechanisch sauber und verdrahten Sie erst danach. Eine stabile Sensorhalterung ist für zuverlässige Zeiten meist wichtiger als eine kompliziertere Software.

## Zuerst die Mechanikteile drucken

Rahmen, Kabelabdeckungen und Elektronikgehäuse werden aus dem [CAD-Paket](../cad_files/README.md) (englisch) gedruckt.
Drucken und passen Sie sie, bevor Sie sonst etwas zuschneiden:

1. Drucken Sie das Testteil für die Sensortasche und prüfen Sie, ob ein ADA2167-Sensorkörper und eine Abdeckung wirklich passen.
2. Exportieren und drucken Sie den Startrahmen (340 mm, drei verzahnte Segmente) und den Zielrahmen (240 mm, einteilig), jeweils links und rechts, dazu die vier Kabelabdeckungen je Rahmenhöhe.
3. Drucken Sie je Modul ein Gehäuseunterteil und einen Schiebedeckel.
4. Stecken Sie die verzahnten Startrahmen-Verbindungen trocken zusammen, bevor Sie kleben.

## Mechanischer Aufbau

1. Markieren Sie Start- und Ziellinie rechtwinklig zu jeder Spur.
2. Montieren Sie Sender und Empfänger einer Lichtschranke gegenüber, auf gleicher Höhe und außerhalb der Fahrzeugkontaktzone.
   Im gedruckten Rahmen übernehmen das die Sensortaschen: Die Optiken stehen sich senkrecht durch die 15,4 × 7,7 mm großen Schlitze gegenüber.
3. Setzen Sie die Sensoren von der äußeren Kabelkanalseite ein, führen Sie die Leitungen durch die verdeckten Kanäle und schieben Sie die Abdeckungen ein.
4. Schützen Sie die Optik vor direkter Sonne mit kurzen Blenden. Prüfen Sie bei der realen Veranstaltungsbeleuchtung.
5. Kleben Sie das Elektronikgehäuse auf die 60 mm breite Montagefläche des Rahmens. Das START-Modul sitzt am Startrahmen, das ZIEL-Modul am Zielrahmen; so bleiben die Sensorleitungen kurz.

## Elektrischer Aufbau

1. Noch ohne ESP32 prüfen: Versorgungsspannung jedes Empfängers messen und sicherstellen, dass die weiße Ader der Open-Collector-Ausgang ist.
2. 5 V und GND über die WAGO-Klemmen verteilen, Sensoradern mit Silikonlitze und LT-1-Klemmen verlängern und jede weiße Ader auf eine eigene Schraubklemme des Breakout-Boards legen. Für den ADA2167 ist kein Pegelwandler nötig, siehe [Verdrahtung](WIRING.md).
3. Ein Modul nach dem anderen starten. Im Health-Status müssen beide Strahlen als `clear` erscheinen.
4. Erst nach erfolgreichem Sensortest WLAN und optional RS485 aktivieren.

Einen fertigen Arduino-Sketch, die Board-Einstellungen und die Bibliotheksliste finden Sie im
[ESP32-Sensor-Firmware-Leitfaden](../esp32/ESP32_SENSOR_FIRMWARE_GUIDE.md) (englisch).

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

**Gemeinsame Ressourcen (englisch):** [CAD-Paket](../cad_files/README.md) · [ESP32-Sensor-Firmware-Leitfaden](../esp32/ESP32_SENSOR_FIRMWARE_GUIDE.md)

**Alle Themen:** [Projektüberblick](PROJECT.md) · [Materialliste](MATERIALS.md) · [Architektur](ARCHITECTURE.md) · [Verdrahtung](WIRING.md) · [Firmware](FIRMWARE.md) · [Protokoll](PROTOCOL.md) · **Aufbau** · [Tests](TESTING.md) · [ESP-Agentenauftrag](ESP_AGENT_GUIDE.md) · [Racing-Manager-Integration](AGENT_RACING_MANAGER_INTEGRATION.md)
