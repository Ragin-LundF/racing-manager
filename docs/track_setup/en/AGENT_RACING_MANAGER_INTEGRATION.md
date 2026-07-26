# Agent brief: Ragin-LundF/racing-manager integration

This is the handoff to the person or agent integrating the hardware into the existing application. It adds hardware support without replacing the working participant and race-management features.

## Scope and limits

Implement the central Raspberry Pi hardware integration in the existing project. First inspect the repository's architecture, framework, authentication, real-time channel, and data model. Reuse existing patterns; do not create a second web server or parallel state store.

The repository was not locally available when this document was created, so filenames and framework details are intentionally not prescribed. The binding device contract is [PROTOCOL.md](PROTOCOL.md).

## Functional scope

- WebSocket endpoint for ESP32 devices with registration, heartbeats, and reconnect.
- Module registry: `device_id`, role, boot ID, firmware, health, transport, last contact, sensor state.
- Race adapter: `race.arm`, `race.start`, `race.reset`; accept valid events for active lanes only.
- Persist raw start/finish events, calculated lane time, timing uncertainty, and result quality separately.
- Deduplicate by `device_id + boot_id + sequence`; ACK only after durable acceptance.
- Time synchronization service and visible warning when uncertainty exceeds threshold.
- Feed health/operator UI and spectator view using the project’s existing live-update mechanism.

## Order of work

1. Identify existing domain models for races, lanes, results, and live events.
2. Create a bounded hardware port; transport handlers must not contain race rules.
3. Implement state transitions and persistence transactionally.
4. Let API/UI consume central hardware state only.
5. Add a simulator/fake ESP for local tests.

## Security and quality rules

- An unknown device may not control a race; allow devices via configuration or pairing.
- Handle message size, JSON validation, rate limits, and disconnects.
- An ESP reboot or contradictory event makes the affected race visibly invalid/review-required, never silently successful.
- Do not mix hardware and server time: retain raw timestamps, sync offset, uncertainty, and calculated time independently.

## Acceptance

Automated tests cover registration, missing heartbeats, ACK/deduplication, invalid event order, one- and two-lane races, and reconnection. Document the actual integration entry point and configuration in the project documentation.

---

**Navigation:** [← ESP agent brief](ESP_AGENT_GUIDE.md) · [Back to start](PROJECT.md) · [Deutsch](../de/AGENT_RACING_MANAGER_INTEGRATION.md)

**All topics:** [Project overview](PROJECT.md) · [Materials](MATERIALS.md) · [Architecture](ARCHITECTURE.md) · [Wiring](WIRING.md) · [Firmware](FIRMWARE.md) · [Protocol](PROTOCOL.md) · [Setup](SETUP.md) · [Testing](TESTING.md) · [ESP agent brief](ESP_AGENT_GUIDE.md) · **Racing Manager integration**
