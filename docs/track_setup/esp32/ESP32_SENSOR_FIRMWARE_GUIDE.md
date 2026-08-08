# ESP32 Firmware for Model Car Light Barriers

This file is the technical reference for the light-barrier modules of the
model-car race track. It documents the wiring, required Arduino packages, and
the message format. The firmware itself lives next to this file, as two
ready-to-upload Arduino sketches:

| Sketch | Board |
| --- | --- |
| [`racing-sensor-ino-prj/LightSensor/`](racing-sensor-ino-prj/LightSensor/LightSensor.ino) | ESP32 DevKit with **32 pins and no integrated display** |
| [`racing-sensor-ino-prj/LightSensorDisplay/`](racing-sensor-ino-prj/LightSensorDisplay/LightSensorDisplay.ino) | **30-pin board with an integrated small LCD** |

Each sketch monitors one light barrier and sends an event over Wi-Fi/WebSocket
to the Raspberry Pi running Racing Manager. Use the same sketch for all four
measuring points; only the configuration values at the top of the sketch change.

The reference design in [`../en/ARCHITECTURE.md`](../en/ARCHITECTURE.md) groups
two measuring points per module — one ESP32 at the start and one at the finish,
each with two sensor inputs. The sketches here deliberately handle a single
input so the hardware can be proven quickly; extend them with a second pin
(GPIO 17, see [`../en/WIRING.md`](../en/WIRING.md)) for the full two-lane module.

**Where this fits:** this guide is shared by both language editions of the track
documentation. Start at [`../README.md`](../README.md) for the full package, use
[`../en/MATERIALS.md`](../en/MATERIALS.md) / [`../de/MATERIALS.md`](../de/MATERIALS.md)
for the parts that are actually used, and [`../cad_files/README.md`](../cad_files/README.md)
for the printed frames and housings that hold this hardware.

## 1. Hardware and wiring

### 1.1 Light barrier used

The [Adafruit ADA2167](https://www.amazon.de/dp/B01BU6YBWU) consists of an IR
transmitter and an IR receiver. It runs from 3.3–5.5 V, reacts in under 2 ms,
and reaches about 25 cm:

| Part | Wire colour | Function | ESP32 connection |
| --- | --- | --- | --- |
| IR transmitter | Red | Power | 5V / VIN |
| IR transmitter | Black | Ground | GND |
| IR receiver | Red | Power | 5V / VIN |
| IR receiver | Black | Ground | GND |
| IR receiver | White | Open-collector signal | GPIO 16 |

The transmitter and receiver are powered in parallel. Use two 3-position
[WAGO 221-413](https://www.amazon.de/dp/B07NKCWBST) connectors per measuring
module. Extend single leads with
[20 AWG silicone wire](https://www.amazon.de/dp/B0C7T9P8G7) and
[LT-1 push-in splices](https://www.amazon.de/dp/B0G6K9WJ2B); the ESP32 sits on a
[30-pin breakout board](https://www.amazon.de/dp/B0F6CLP43C) whose screw
terminals take the sensor wires without soldering:

```text
5 V connector: ESP32 5V/VIN ─┬─ Transmitter red
                             └─ Receiver red

GND connector: ESP32 GND    ─┬─ Transmitter black
                             └─ Receiver black

Signal:       Receiver white ─── GPIO 16
```

The white receiver output is **open collector**. The sketch therefore enables
the ESP32's internal 3.3 V pull-up on GPIO 16 (`INPUT_PULLUP`). When triggered,
the receiver pulls the pin to GND. Do not connect an external 5 V pull-up to
the white signal wire.

### 1.2 Electrical notes

- The sensor and ESP32 must share the same ground (GND).
- The ADA2167 does not need an optocoupler if sensor and ESP32 wiring is short
  and both are in the same enclosure.
- The examples assume **LOW = light barrier interrupted**. If your actual
  setup behaves the opposite way, set `SENSOR_ACTIVE_LEVEL` to `HIGH`.
- GPIO 16 is reserved as the sensor input on both boards documented here.

## 2. Arduino IDE setup

### Board and upload

In Arduino IDE:

1. Open **Tools → Board → Boards Manager**.
2. Search for and install **ESP32 by Espressif Systems**.
3. Select **ESP32 Dev Module** as the board.

Recommended settings:

| Setting | Value |
| --- | --- |
| Board | ESP32 Dev Module |
| Upload Speed | 115200 |
| Flash Size | 16MB (128Mb), if the board has this capacity and the option is available |
| Port | the ESP32 USB port shown by Arduino IDE |

If uploading fails with `Unable to verify flash chip connection`, first unplug
everything except USB, try another data cable, and connect directly to the
computer rather than through a USB hub.

### Required libraries

Install these through **Sketch → Include Library → Manage Libraries**:

| Library | Author / search term | Without display | With display |
| --- | --- | :---: | :---: |
| WebSockets | Markus Sattler / Links2004 | Yes | Yes |
| ArduinoJson | Benoit Blanchon | Yes | Yes |
| Adafruit GFX Library | Adafruit | No | Yes |
| Adafruit ST7735 and ST7789 Library | Adafruit | No | Yes |

`WiFi.h`, `SPI.h`, and `Arduino.h` are part of the ESP32 board package and do
not need to be installed separately.

## 3. Common message format

The sketches speak the envelope from [`../en/PROTOCOL.md`](../en/PROTOCOL.md).
Right after the WebSocket connects, a module registers itself once:

```json
{
  "v": 1,
  "type": "device.register",
  "device_id": "lane-1-start",
  "boot_id": "a1b2c3d4e5f60718",
  "role": "start",
  "firmware": "1.0.0",
  "capabilities": ["beam_sensor", "wifi"]
}
```

Every second while connected, it reports a heartbeat with its own beam state:

```json
{
  "v": 1,
  "type": "device.heartbeat",
  "device_id": "lane-1-start",
  "boot_id": "a1b2c3d4e5f60718",
  "uptime_ms": 64321,
  "transport": "wifi",
  "sensors": { "lane_1": "clear" }
}
```

A triggered light barrier sends:

```json
{
  "v": 1,
  "type": "sensor.event",
  "message_id": "a1b2c3d4e5f60718-1",
  "device_id": "lane-1-start",
  "boot_id": "a1b2c3d4e5f60718",
  "sequence": 1,
  "role": "start",
  "lane": 1,
  "event": "beam_broken",
  "local_timestamp_us": 123456
}
```

| Field | Meaning |
| --- | --- |
| `device_id` | Unique device ID, for example `lane-1-start` |
| `boot_id` | Changes every boot; two random hex words, enough to tell a reconnect from a reset |
| `role` | `start` or `finish` |
| `lane` | Lane number: `1` or `2` |
| `event` | Always `beam_broken` in this firmware |
| `local_timestamp_us` | Microseconds since the ESP32 was powered on |
| `sequence` | Consecutive local event number, also embedded in `message_id` |

The backend does not yet implement the `race.arm`/`race.armed`/`race.start`/
`race.reset` handshake or `time.sync_request`/`time.sync_response` — see
[`../en/FIRMWARE.md`](../en/FIRMWARE.md) — so the sketches never wait for or
send those messages; `race_id`, `sync_timestamp_us`, and `sync_uncertainty_us`
are simply omitted from `sensor.event`. `event.ack` is likewise not sent back,
since it is part of the same not-yet-implemented handshake.

The exact WebSocket address must match Racing Manager. The examples use
Raspberry Pi address `192.168.10.1`, port `8080`, and path `/hardware/esp32/ws`.
Change these values if the server configuration differs.

## 4. Sketch: 32-pin ESP32 without integrated display

Suitable for a standard ESP32 DevKit/WROOM-32 without a display. The Serial
Monitor runs at 115200 baud and shows status messages and JSON events.

**Sketch:** [`racing-sensor-ino-prj/LightSensor/LightSensor.ino`](racing-sensor-ino-prj/LightSensor/LightSensor.ino)

Open the folder `racing-sensor-ino-prj/LightSensor` in the Arduino IDE, adjust
the configuration block at the top of the sketch (see section 6), and upload.

## 5. Sketch: 30-pin ESP32 with integrated display

The reference build uses the
[diymore ESP32 board with a 1.96-inch LCD (CH340, USB-C)](https://www.amazon.de/dp/B0DWWB63YZ).
The pin map below was documented on the closely related 1.9-inch `B0DK399Y9Q`
module with ESP32-WROOM-32 and ST7789 display (170 × 320 pixels). **Verify it
against the board you actually received** before wiring — if the display stays
dark or the sensor pin misbehaves, the pin assignment is the first thing to
check. The display uses these GPIOs:

| Display function | GPIO |
| --- | ---: |
| MOSI | 23 |
| SCLK | 18 |
| CS | 15 |
| DC | 2 |
| Reset | 4 |
| Backlight | 32 |

GPIO 16 remains free for the white light-barrier signal wire. The display is
in portrait orientation and acts as a signal light: green = clear lane, red =
light barrier interrupted. The sketch draws the static layout once and then
updates only the small regions whose values changed. This avoids the visible
flicker caused by repeatedly clearing the whole ST7789 framebuffer over SPI.
See the performance-oriented UI mockup at
[`display-performance-mockup.svg`](display-performance-mockup.svg).

**Sketch:** [`racing-sensor-ino-prj/LightSensorDisplay/LightSensorDisplay.ino`](racing-sensor-ino-prj/LightSensorDisplay/LightSensorDisplay.ino)

Open the folder `racing-sensor-ino-prj/LightSensorDisplay` in the Arduino IDE,
adjust the configuration block at the top of the sketch (see section 6), and
upload. Note that this sketch initialises Wi-Fi before the display; keep that
order if you modify `setup()`.

## 6. Module configurations

Both sketches start with the same configuration block. Change only these values
per measuring point; everything below the block is identical across all four
modules.

| Constant | Meaning |
| --- | --- |
| `WIFI_SSID`, `WIFI_PASSWORD` | Credentials of the Raspberry Pi access point. Do not commit a real password. |
| `MODULE_ID`, `LANE_NUMBER`, `MODULE_POSITION` | Identity of this measuring point, see the table below |
| `WEBSOCKET_HOST`, `WEBSOCKET_PORT`, `WEBSOCKET_PATH` | Racing Manager endpoint |
| `SENSOR_PIN` | GPIO carrying the receiver's white wire; `16` on both documented boards |
| `SENSOR_ACTIVE_LEVEL` | `LOW` when the beam being broken pulls the pin down — correct for the ADA2167 with `INPUT_PULLUP` |
| `DEBOUNCE_US` | Minimum gap between two accepted edges |

| Measuring point | `MODULE_ID` | `LANE_NUMBER` | `MODULE_POSITION` |
| --- | --- | ---: | --- |
| Lane 1 start | `lane-1-start` | 1 | `start` |
| Lane 1 finish | `lane-1-finish` | 1 | `finish` |
| Lane 2 start | `lane-2-start` | 2 | `start` |
| Lane 2 finish | `lane-2-finish` | 2 | `finish` |

## 7. Further development and important limitations

- The example sends events as soon as the sensor triggers. It does not
  implement race logic; that belongs in Racing Manager on the Raspberry Pi,
  which times each lane from the delay between its start and finish
  `beam_broken` events using its own receipt time — not a device timestamp.
- `local_timestamp_us` overflows after roughly 71 minutes and is not used for
  timing by the backend for exactly that reason. It is device-local only
  (useful for correlating log lines on one module), never compared across
  devices.
- The `race.arm`/`race.armed`/`race.start`/`race.reset` handshake and the
  `time.sync_request`/`time.sync_response` exchange from
  [`../en/PROTOCOL.md`](../en/PROTOCOL.md) are part of the protocol but not
  implemented here, on either side: the backend gateway currently refuses to
  start with them enabled. Add the incoming-message handling (`WStype_TEXT`)
  and reply logic to these sketches only once that backend work exists.
- For a wired expansion, keep the sensor-event generation and replace or add to
  the WebSocket transport with RS485.
- For long wires or external 12/24 V sensors, use a suitable isolated digital
  input or optocoupler. The direct GPIO 16 wiring documented here is for the
  ADA2167 with short wires and a shared 5 V supply.

---

**Track documentation:** [Package overview](../README.md) ·
[English project overview](../en/PROJECT.md) · [Deutscher Projektüberblick](../de/PROJECT.md)

**Directly related chapters:** [Materials](../en/MATERIALS.md) ·
[Wiring](../en/WIRING.md) · [Firmware specification](../en/FIRMWARE.md) ·
[Protocol](../en/PROTOCOL.md) · [CAD package](../cad_files/README.md)
