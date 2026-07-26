# System architecture and trigger plan

This chapter explains which device owns which job. That matters because the Raspberry Pi organizes races while ESP32 boards capture fast sensor pulses locally. It also keeps the design understandable when a starting gate is added later.

## Overview

```text
 Registration station ───── local Wi-Fi, no Internet ── Raspberry Pi
 (participants + vehicles)                               Racing Manager + API + UI
 Race-director notebook ─── LAN or local Wi-Fi ─────────┤
 Race-director notebook ─── HDMI/USB-C/DisplayPort ── projector or external TV
                                                            │ WebSocket (primary)
                               ┌────────────────────────────┴────────────────────────────┐
                               │                                                         │
                         ESP32 START                                               ESP32 FINISH
                         L1 start, L2 start                                        L1 finish, L2 finish
                               ╰──────────────── optional RS485 ─────────────────────────╯
```

The Raspberry Pi is not a time-critical sensor controller. It manages races, participants, UI, storage, and connectivity. Each ESP32 debounces local sensors using hardware interrupts and retains events through temporary network loss.

The registration station is one notebook or tablet at the check-in desk. It enters names and vehicle data into Racing Manager before the race. Local Wi-Fi is therefore an internal working network, not visitor Wi-Fi. The race director opens the spectator view on their notebook. **The projector or external TV connects directly to that notebook only** (for example through HDMI, USB-C/DisplayPort adapter, or docking station); the Raspberry Pi does not supply a dedicated spectator-display signal.

## Assignment

| Measurement point | Owner | Sensor ID | Event |
|---|---|---|---|
| Lane 1 start | ESP32 START | `start.lane_1` | `beam_broken` |
| Lane 2 start | ESP32 START | `start.lane_2` | `beam_broken` |
| Lane 1 finish | ESP32 FINISH | `finish.lane_1` | `beam_broken` |
| Lane 2 finish | ESP32 FINISH | `finish.lane_2` | `beam_broken` |

## States and triggers

```text
IDLE → HEALTHY → ARMED → RUNNING → FINISHED → IDLE
                 │          │
             sensor fault   └─ start/finish events
```

1. Racing Manager checks heartbeats, clear sensors, and valid configuration.
2. The operator selects active lanes and sends `race.arm` with a `race_id`.
3. Both modules reply `race.armed`; blocked or faulty sensors prevent a start.
4. `race.start` starts the race. With a future gate, START opens gates together and sends the start reference to FINISH.
5. The start beam records actual lane departure; the finish beam completes that lane.
6. The Pi builds results, status, and spectator view. `race.reset` clears only transient module race state.

## Timebase and accuracy

- Every ESP32 timestamps interrupts with its local microsecond clock; interrupts never draw displays, serialize JSON, or call Wi-Fi.
- Before `race.arm`, the Pi runs multiple time-sync rounds (`time.sync_request`/`time.sync_response`) and stores module offset and estimated uncertainty.
- **Wi-Fi mode:** target a model-car event display with an `estimated` quality flag and stated synchronization uncertainty; do not claim guaranteed sub-millisecond scoring.
- **RS485 mode:** preferred for scored runs; it carries the same messages more robustly. A shared `race.start_reference` gives a particularly consistent race reference.
- For very short or record-relevant runs, time from the shared gate release and use start beams for false-start/pass validation. A start-to-finish time across two clocks is only as accurate as their synchronization.

## Network

The Pi publishes a WPA2-protected SSID such as `RacingManager`, with no Internet requirement. Connect only `start-01`, `finish-01`, and the registration station. Give the Pi a stable address, for example `192.168.50.1`; reserve ESP addresses or identify devices by ID. For events, a dedicated travel router makes a more stable AP while the Pi remains connected by Ethernet.

---

**Navigation:** [← Materials](MATERIALS.md) · [Next: Wiring →](WIRING.md) · [Deutsch](../de/ARCHITECTURE.md)

**All topics:** [Project overview](PROJECT.md) · [Materials](MATERIALS.md) · **Architecture** · [Wiring](WIRING.md) · [Firmware](FIRMWARE.md) · [Protocol](PROTOCOL.md) · [Setup](SETUP.md) · [Testing](TESTING.md) · [ESP agent brief](ESP_AGENT_GUIDE.md) · [Racing Manager integration](AGENT_RACING_MANAGER_INTEGRATION.md)
