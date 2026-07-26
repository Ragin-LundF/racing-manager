# Setup and configuration

This step turns individual parts into a track. Build the mechanics neatly first, then wire it. A stable sensor bracket is usually more important for reliable timings than more complicated software.

## Mechanical setup

1. Mark start and finish lines perpendicular to each lane.
2. Mount each beam transmitter and receiver opposite one another at the same height and outside vehicle contact range.
3. Give every sensor its own bracket; avoid a shared vibrating rail.
4. Shade optics from direct sun with short hoods. Test under actual event lighting.
5. Put the START ESP near start sensors and the FINISH ESP near finish sensors. Keep sensor wires short.

## Electrical setup

1. Before connecting an ESP32, measure every receiver supply and output level.
2. Install level shifter/optocoupler, then connect GPIO.
3. Power one module at a time. Health must show both beams as `clear`.
4. Enable Wi-Fi and optional RS485 only after the sensor test passes.

## Raspberry Pi as internal local Wi-Fi

Give the Pi a fixed Wi-Fi IP, for example `192.168.50.1`, DHCP range `192.168.50.100–.199`, a WPA2 password, and a local DNS name such as `racing.local`. This Wi-Fi is for the ESP32 modules and one registration station, not visitors. Exact setup depends on the Pi OS release and NetworkManager; do not commit the selected SSID, password, or reservations to the firmware repository.

For the event, protect Pi and AP/router against power loss, avoid roaming or Internet dependencies, and test an operator notebook on the local network in advance.

## Pre-race sequence

1. Start the Pi and Racing Manager.
2. Power START and FINISH; both register and report health.
3. Align sensors until all four report `clear`.
4. Select active lanes, send `race.arm`, and wait for both confirmations.
5. Start the race; spectator view stays read-only.

Open the spectator view on the race-director notebook. Then connect the projector or TV to that notebook and select duplicate or extended display there. The Raspberry Pi is only the server and network hub; the projector is not expected to connect to it.

---

**Navigation:** [← Protocol](PROTOCOL.md) · [Next: Testing →](TESTING.md) · [Deutsch](../de/SETUP.md)

**All topics:** [Project overview](PROJECT.md) · [Materials](MATERIALS.md) · [Architecture](ARCHITECTURE.md) · [Wiring](WIRING.md) · [Firmware](FIRMWARE.md) · [Protocol](PROTOCOL.md) · **Setup** · [Testing](TESTING.md) · [ESP agent brief](ESP_AGENT_GUIDE.md) · [Racing Manager integration](AGENT_RACING_MANAGER_INTEGRATION.md)
