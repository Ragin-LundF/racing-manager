# Protocol: Racing Manager ↔ ESP32

This chapter is the shared “language contract” between software and hardware. It describes which messages are allowed and what they mean, so the web application can evolve independently of whether modules currently use Wi-Fi or later RS485.

The primary transport is a persistent WebSocket. Messages are UTF-8 JSON, one message per WebSocket frame. RS485 may carry the same JSON payload with a length prefix, CRC16, and sequence number.

## Envelope

Every message carries a version and unique identifier. This makes it possible to detect old or duplicate messages without scoring one timing event twice.

```json
{"v":1,"type":"sensor.event","message_id":"uuid","device_id":"start-01","sent_at_us":123456789}
```

`v` is the protocol version. Ignore unknown optional fields; answer unknown required `type` values with `error.unsupported`.

## Registration and health

```json
{"v":1,"type":"device.register","device_id":"finish-01","boot_id":"a8c1","role":"finish","firmware":"0.1.0","capabilities":["beam_sensor","wifi","rs485"]}
{"v":1,"type":"device.heartbeat","device_id":"finish-01","boot_id":"a8c1","uptime_ms":64321,"transport":"wifi","sensors":{"lane_1":"clear","lane_2":"blocked"}}
```

Heartbeat interval: 1 s; offline after five missed heartbeats. The server acknowledges accepted events:

```json
{"v":1,"type":"event.ack","message_id":"event-uuid"}
```

## Race control

```json
{"v":1,"type":"race.arm","race_id":"race-42","lanes":[1,2],"sync_epoch_us":1760000000000}
{"v":1,"type":"race.armed","race_id":"race-42","device_id":"start-01","sensors_ready":true}
{"v":1,"type":"race.start","race_id":"race-42","start_reference_us":1760000005000}
{"v":1,"type":"race.reset","race_id":"race-42"}
```

The server must not send `race.start` until it has positive `race.armed` replies. In the future, the START module opens its gates in the same command sequence.

## Sensor event

```json
{"v":1,"type":"sensor.event","message_id":"d2","device_id":"finish-01","boot_id":"a8c1","sequence":77,"race_id":"race-42","role":"finish","lane":1,"event":"beam_broken","local_timestamp_us":4567890,"sync_timestamp_us":1760000008329470,"sync_uncertainty_us":3500}
```

Racing Manager accepts events only for active lanes in the expected state. Per lane, start precedes finish. Store raw data and calculated result separately, including quality.

## Time synchronization

Before arming, the server runs at least five rounds:

```json
{"v":1,"type":"time.sync_request","nonce":"n","server_send_us":1760000000000}
{"v":1,"type":"time.sync_response","nonce":"n","device_receive_us":900000,"device_send_us":900040}
```

The server selects the sample with the shortest round-trip and shares its calculated offset in `race.arm`. Persist the estimated uncertainty and show it in the UI.

---

**Navigation:** [← Firmware](FIRMWARE.md) · [Next: Setup →](SETUP.md) · [Deutsch](../de/PROTOCOL.md)

**All topics:** [Project overview](PROJECT.md) · [Materials](MATERIALS.md) · [Architecture](ARCHITECTURE.md) · [Wiring](WIRING.md) · [Firmware](FIRMWARE.md) · **Protocol** · [Setup](SETUP.md) · [Testing](TESTING.md) · [ESP agent brief](ESP_AGENT_GUIDE.md) · [Racing Manager integration](AGENT_RACING_MANAGER_INTEGRATION.md)
