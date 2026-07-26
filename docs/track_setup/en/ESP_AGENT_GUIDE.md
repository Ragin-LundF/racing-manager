# Agent brief: ESP32 firmware

This file can be handed unchanged to an AI agent or a developer. It uses the previous chapters as its technical specification.

## Objective

Implement one shared firmware for `start-01` and `finish-01` following [PROTOCOL.md](PROTOCOL.md), [FIRMWARE.md](FIRMWARE.md), and [WIRING.md](WIRING.md). No GPIO may be hard-coded invisibly; roles, pins, sensor polarity, and server address are configurable.

## Non-negotiable requirements

- Keep ISR minimal: only `esp_timer_get_time()`, sensor ID, sequence into queue; no network/display calls.
- Enforce active race state strictly; first valid event only per sensor/race.
- Heartbeat every second, register on every connection, event ACK and persistent retry.
- Bounded offline queue with visible overflow and duplicate protection (`boot_id`, `sequence`).
- Implement time sync and output `sync_uncertainty_us`; do not pretend to have false precision when quality is poor.
- Wi-Fi primary, RS485 behind replaceable transport interface; first increment may ship RS485 as a clearly marked disabled feature flag.

## Deliverables

1. Build and flash instructions.
2. Example configuration for START and FINISH.
3. Unit tests for state machine, debounce, and event deduplication.
4. Hardware-in-the-loop test script or documented manual test.
5. Brief README listing supported boards and pin conflicts.

## Acceptance

Demonstrate with logs/tests: two parallel lanes, single-lane race, Wi-Fi reconnect without a lost scored event, sensor failure before `race.arm`, and reboot during a race. Version any protocol change and update the central integration contract together.

---

**Navigation:** [← Testing](TESTING.md) · [Next: Racing Manager integration →](AGENT_RACING_MANAGER_INTEGRATION.md) · [Deutsch](../de/ESP_AGENT_GUIDE.md)

**All topics:** [Project overview](PROJECT.md) · [Materials](MATERIALS.md) · [Architecture](ARCHITECTURE.md) · [Wiring](WIRING.md) · [Firmware](FIRMWARE.md) · [Protocol](PROTOCOL.md) · [Setup](SETUP.md) · [Testing](TESTING.md) · **ESP agent brief** · [Racing Manager integration](AGENT_RACING_MANAGER_INTEGRATION.md)
