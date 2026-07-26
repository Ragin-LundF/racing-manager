# Protokoll: Racing Manager ↔ ESP32

Dieses Kapitel ist der gemeinsame „Sprachvertrag“ zwischen Software und Hardware. Es beschreibt, welche Nachrichten erlaubt sind und was sie bedeuten. Dadurch kann die Webanwendung unabhängig davon weiterentwickelt werden, ob die Module über WLAN oder später über RS485 verbunden sind.

Der primäre Transport ist eine persistente WebSocket-Verbindung. Nachrichten sind UTF-8-JSON, eine Nachricht pro WebSocket-Frame. RS485 kann dieselben JSON-Nutzdaten mit Längenpräfix, CRC16 und Sequenznummer übertragen.

## Umschlag

Alle Nachrichten tragen eine Version und eine eindeutige Kennung. So lassen sich alte oder doppelt empfangene Nachrichten erkennen, ohne eine Zeitmessung versehentlich zweimal zu werten.

```json
{"v":1,"type":"sensor.event","message_id":"uuid","device_id":"start-01","sent_at_us":123456789}
```

`v` ist die Protokollversion. Unbekannte, optionale Felder werden ignoriert; unbekannte Pflicht-`type`-Werte werden mit `error.unsupported` beantwortet.

## Registrierung und Health

```json
{"v":1,"type":"device.register","device_id":"finish-01","boot_id":"a8c1","role":"finish","firmware":"0.1.0","capabilities":["beam_sensor","wifi","rs485"]}
{"v":1,"type":"device.heartbeat","device_id":"finish-01","boot_id":"a8c1","uptime_ms":64321,"transport":"wifi","sensors":{"lane_1":"clear","lane_2":"blocked"}}
```

Heartbeat-Intervall: 1 s; offline nach 5 ausbleibenden Heartbeats. Der Server antwortet auf empfangene Events:

```json
{"v":1,"type":"event.ack","message_id":"uuid-des-events"}
```

## Rennsteuerung

```json
{"v":1,"type":"race.arm","race_id":"race-42","lanes":[1,2],"sync_epoch_us":1760000000000}
{"v":1,"type":"race.armed","race_id":"race-42","device_id":"start-01","sensors_ready":true}
{"v":1,"type":"race.start","race_id":"race-42","start_reference_us":1760000005000}
{"v":1,"type":"race.reset","race_id":"race-42"}
```

`race.start` darf der Server erst nach positiven `race.armed`-Antworten senden. Das Startmodul öffnet später im selben Befehlsablauf seine Sperren.

## Sensorereignis

```json
{"v":1,"type":"sensor.event","message_id":"d2","device_id":"finish-01","boot_id":"a8c1","sequence":77,"race_id":"race-42","role":"finish","lane":1,"event":"beam_broken","local_timestamp_us":4567890,"sync_timestamp_us":1760000008329470,"sync_uncertainty_us":3500}
```

Der Racing Manager akzeptiert nur Ereignisse aktiver Spuren im erwarteten Zustand. Pro Spur gilt: Start vor Ziel. Er speichert Rohdaten und berechnete Zeit getrennt, einschließlich Zeitqualität.

## Zeitsynchronisation

Vor dem Scharfschalten führt der Server mindestens fünf Runden aus:

```json
{"v":1,"type":"time.sync_request","nonce":"n","server_send_us":1760000000000}
{"v":1,"type":"time.sync_response","nonce":"n","device_receive_us":900000,"device_send_us":900040}
```

Der Server wählt die Probe mit kleinster Laufzeit und teilt den ermittelten Offset in `race.arm` mit. Diese Implementierung muss die geschätzte Unsicherheit persistieren und im UI zeigen.

---

**Navigation:** [← Firmware](FIRMWARE.md) · [Weiter: Aufbau →](SETUP.md) · [English](../en/PROTOCOL.md)

**Alle Themen:** [Projektüberblick](PROJECT.md) · [Materialliste](MATERIALS.md) · [Architektur](ARCHITECTURE.md) · [Verdrahtung](WIRING.md) · [Firmware](FIRMWARE.md) · **Protokoll** · [Aufbau](SETUP.md) · [Tests](TESTING.md) · [ESP-Agentenauftrag](ESP_AGENT_GUIDE.md) · [Racing-Manager-Integration](AGENT_RACING_MANAGER_INTEGRATION.md)
