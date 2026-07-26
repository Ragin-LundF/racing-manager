# Two-lane model-car race track

This package documents a mobile, reproducible two-lane race track. A Raspberry Pi runs **Ragin-LundF/racing-manager**, the local Wi-Fi, and the user interface. Two ESP32 modules acquire the beam sensors: one at the start and one at the finish.

No electronics or programming experience is required to follow this guide. Work through it in order and do not skip safety or test steps. Terms such as GPIO, ESP32, and beam sensor are explained where they are used.

## Start here

1. Buy the [materials](MATERIALS.md) and read the safety notes.
2. Decide on the [architecture](ARCHITECTURE.md) and [wiring](WIRING.md) before building.
3. Install the hardware using [setup](SETUP.md).
4. Implement the ESP firmware using [firmware](FIRMWARE.md) and the [protocol](PROTOCOL.md).
5. Commission it with [testing](TESTING.md).

## Scope and reference assumptions

- Two lanes; one start and one finish line each: four optical pass events.
- Track length: about 10–20 m; Wi-Fi is the default transport, RS485 over CAT cable is optional.
- ESP32 modules: one standard ESP32 DevKit at start and one at finish, each with two sensor inputs.
- Raspberry Pi: central race logic, storage, web UI, and local Wi-Fi access point.
- Wi-Fi: only for the two ESP32 modules and **one registration station** that enters participant and vehicle data; it is not public participant Wi-Fi.
- Version 1 has no starting gate. A future shared gate (servo/solenoid) belongs to the start module.

## Documents

| Document | Purpose |
|---|---|
| [MATERIALS.md](MATERIALS.md) | Shopping list and selection criteria |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Responsibilities, network, and triggers |
| [WIRING.md](WIRING.md) | Pinout and wiring plan |
| [FIRMWARE.md](FIRMWARE.md) | ESP implementation and configuration |
| [PROTOCOL.md](PROTOCOL.md) | Raspberry Pi ↔ ESP32 message contract |
| [SETUP.md](SETUP.md) | Mechanical and electrical setup |
| [TESTING.md](TESTING.md) | Commissioning and event checklist |
| [ESP_AGENT_GUIDE.md](ESP_AGENT_GUIDE.md) | Work order for an ESP firmware agent |
| [AGENT_RACING_MANAGER_INTEGRATION.md](AGENT_RACING_MANAGER_INTEGRATION.md) | Work order for central integration |

## Important accuracy note

Two separate ESP32 boards have **no shared clock** without synchronization. Wi-Fi timestamps are suitable for a visible race display, but must not be presented as guaranteed sub-millisecond timing. This design therefore uses a synchronized race reference and labels measurement quality. For reliable, highly accurate results, use RS485 or a shared start-gate reference. Details: [Architecture](ARCHITECTURE.md#timebase-and-accuracy).

The German edition is at [`../de/PROJECT.md`](../de/PROJECT.md).

---

**Navigation:** Start · [Next: Materials →](MATERIALS.md) · [Deutsch](../de/PROJECT.md)

**All topics:** [Project overview](PROJECT.md) · [Materials](MATERIALS.md) · [Architecture](ARCHITECTURE.md) · [Wiring](WIRING.md) · [Firmware](FIRMWARE.md) · [Protocol](PROTOCOL.md) · [Setup](SETUP.md) · [Testing](TESTING.md) · [ESP agent brief](ESP_AGENT_GUIDE.md) · [Racing Manager integration](AGENT_RACING_MANAGER_INTEGRATION.md)
