# ESP32 firmware

Firmware is the small program on the ESP32. It reads beam sensors, remembers the precise local time of a beam break, then reports that event to the Raspberry Pi. This chapter also works as a specification for a developer or AI agent.

## Common requirements

- ESP-IDF or Arduino-ESP32; ESP-IDF with FreeRTOS is recommended for event use.
- One firmware project with `module_role: start|finish`, not four special builds.
- The interrupt service routine (ISR) only writes timestamp, sensor ID, and sequence number into a lock-free/FreeRTOS queue.
- A task handles debouncing, race state, persistent event queue, and transport.
- Optional display refreshes at most once per second or on a state change, never in the ISR.

## Configuration

The configuration file makes the same ESP32 program become a start or finish module. Do not change it during a race, and do not keep Wi-Fi passwords in publicly shared example files.

```yaml
device_id: start-01
module_role: start
gpio:
  lane_1: 32
  lane_2: 33
sensor:
  active_level: 0
  debounce_us: 15000
network:
  ssid: RacingManager
  server_url: ws://192.168.50.1:8080/hardware/ws
transport:
  primary: wifi
  rs485_fallback: false
```

The finish module uses `device_id: finish-01` and `module_role: finish`. Verify `active_level` with the actual sensor: the health view must say `clear` when the beam is clear.

## Main flow

```text
Boot → sensor self-test → connect Wi-Fi/RS485 → register → heartbeat
  → race.arm → collect local sensor events → race.start → send events
  → race.reset
```

On network loss, retain events with `event_id` and `sequence` in a bounded queue. After reconnect, resend until the server returns `event.ack`. The server must deduplicate on `device_id + boot_id + sequence`.

## Sensor logic

Only the first valid `beam_broken` for a `race_id`, role, and lane counts. Ignore later edges for at least `debounce_us` and until `race.reset` for that measurement. Report sensor state independently of a race as `sensor.status`.

## Optional display

Show module role, Wi-Fi/RS485 state, server contact, L1/L2 clear/blocked, current `race_id`, last event, and firmware version. Only hard-wire a display board after its actual pinout is documented and compared to [WIRING.md](WIRING.md).

---

**Navigation:** [← Wiring](WIRING.md) · [Next: Protocol →](PROTOCOL.md) · [Deutsch](../de/FIRMWARE.md)

**All topics:** [Project overview](PROJECT.md) · [Materials](MATERIALS.md) · [Architecture](ARCHITECTURE.md) · [Wiring](WIRING.md) · **Firmware** · [Protocol](PROTOCOL.md) · [Setup](SETUP.md) · [Testing](TESTING.md) · [ESP agent brief](ESP_AGENT_GUIDE.md) · [Racing Manager integration](AGENT_RACING_MANAGER_INTEGRATION.md)
