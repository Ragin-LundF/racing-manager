# Setup and configuration

This step turns individual parts into a track. Build the mechanics neatly first, then wire it. A stable sensor bracket is usually more important for reliable timings than more complicated software.

## Print the mechanical parts first

The frames, cable covers, and electronics housings are 3D-printed from the [CAD package](../cad_files/README.md). Print
and dry-fit them before buying or cutting anything else:

1. Print the sensor-pocket test part and check that an ADA2167 sensor body and a cover actually fit.
2. Export and print the start frame (340 mm, three keyed sections) and the finish frame (240 mm, one piece), left and
   right, plus the four cable covers per frame height.
3. Print one electronics housing base and lid per module.
4. Dry-fit the keyed start-frame joints before gluing.

## Mechanical setup

1. Mark start and finish lines perpendicular to each lane.
2. Mount each beam transmitter and receiver opposite one another at the same height and outside vehicle contact range.
   In the printed frame this is done by the sensor pockets: the optics face each other vertically through the 15.4 × 7.7 mm slots.
3. Load the sensors from the outer cable-channel side, route their wires through the concealed channels, and slide the covers in.
4. Shade optics from direct sun with short hoods. Test under actual event lighting.
5. Glue the electronics housing to the 60 mm mounting plate of the frame. The START module sits at the start frame, the FINISH module at the finish frame, so sensor wires stay short.

## Electrical setup

1. Before connecting an ESP32, measure every receiver supply and confirm the white wire is the open-collector output.
2. Distribute 5 V and GND with the WAGO connectors, extend sensor leads with silicone wire and LT-1 splices, and land each white wire on its own screw terminal of the breakout board. No level shifter is needed for the ADA2167; see [Wiring](WIRING.md).
3. Power one module at a time. Health must show both beams as `clear`.
4. Enable Wi-Fi and optional RS485 only after the sensor test passes.

For a ready-to-flash Arduino sketch, board settings, and library list, use the
[ESP32 sensor firmware guide](../esp32/ESP32_SENSOR_FIRMWARE_GUIDE.md).

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

**Shared resources:** [CAD package](../cad_files/README.md) · [ESP32 sensor firmware guide](../esp32/ESP32_SENSOR_FIRMWARE_GUIDE.md)

**All topics:** [Project overview](PROJECT.md) · [Materials](MATERIALS.md) · [Architecture](ARCHITECTURE.md) · [Wiring](WIRING.md) · [Firmware](FIRMWARE.md) · [Protocol](PROTOCOL.md) · **Setup** · [Testing](TESTING.md) · [ESP agent brief](ESP_AGENT_GUIDE.md) · [Racing Manager integration](AGENT_RACING_MANAGER_INTEGRATION.md)
