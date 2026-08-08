# Materials

This chapter is the bill of materials for the reference build. The table below lists the parts that were actually bought
and used; the linked Amazon.de listings document the exact variant. Prices, sellers, and availability change often, so
always check the specification, not just the link.

This step is only for purchasing and preparation. Do not buy parts blindly: with beam sensors, output voltage and output
type are what matter. Each entry states what the part is for and the property that makes it suitable.

## Parts actually used

| Part | Qty. | Product used | Purpose |
|---|---:|---|---|
| ESP32 board with 1.96" LCD | 2 | [diymore ESP32 dev board with 1.96" LCD, CH340, USB-C (2-pack)](https://www.amazon.de/dp/B0DWWB63YZ) | One measuring module at start, one at finish. The built-in display shows lane status, Wi-Fi, and server state without a laptop. |
| ESP32 breakout board, 30-pin | 3 | [ESP32S breakout board, GPIO 1-in-2, for 30-pin ESP32 (3-pack)](https://www.amazon.de/dp/B0F6CLP43C) | Carrier board inside the printed housing. Each GPIO is available twice, as pin header and as screw terminal, so sensor wires attach without soldering. |
| IR through-beam sensor | 4 | [Adafruit IR Break Beam Sensor – 3 mm LEDs (ADA2167)](https://www.amazon.de/dp/B01BU6YBWU) | One transmitter/receiver pair per measuring point: lane 1 and lane 2, start and finish. 3.3–5.5 V, open-collector output, response time < 2 ms. |
| WAGO 221-413 lever connector | 30 pcs pack | [WAGO 221-413, 3-conductor, 0.2–4 mm², transparent](https://www.amazon.de/dp/B07NKCWBST) | 5 V and GND distribution inside each housing: one connector feeds the board plus transmitter and receiver. |
| LT-1 quick splice connector | 38 pcs pack | [JOYELEC LT-1 push-in connectors, 1 in / 1 out, 0.5–2.5 mm²](https://www.amazon.de/dp/B0G6K9WJ2B) | Inline splices for extending a single sensor lead inside the frame's cable channel. |
| Silicone wire set, 20 AWG | 1 set | [SCHDRA silicone wire, 20 AWG / 0.5 mm², 6 colours, 3 m each](https://www.amazon.de/dp/B0C7T9P8G7) | Extension wiring for transmitter and receiver. Fine-stranded silicone stays flexible and fits the printed cable channels; the six colours keep 5 V, GND, and signal unambiguous. |

One 2-pack of ESP32 display boards covers the reference design with two modules (start and finish, two sensors each).
The breakout boards come as a 3-pack, so one stays as a spare.

## Also required, not in the list above

These items were already available or are not link-specific:

| Part | Qty. | Minimum requirement / purpose |
|---|---:|---|
| Raspberry Pi | 1 | Wi-Fi access point, Racing Manager, web UI |
| Raspberry Pi power supply, case, SD card | 1 | Matched to the Pi model and sized correctly |
| 5 V power supply, start | 1 | 2 A; 3–5 A once servos are added |
| 5 V power supply, finish | 1 | 1–2 A |
| USB-C cable | 2 | For flashing and powering the ESP32 boards |
| PETG filament (or a print service) | ~1 kg | Frames, covers, and electronics housings, see [CAD package](../cad_files/README.md) |

## 3D-printed mechanical parts

Sensor brackets and enclosures are not bought — they are printed. The parametric OpenSCAD sources, print settings, and
export scripts are in the [CAD package](../cad_files/README.md), which is shared by both language editions:

- A two-lane U-frame per measuring point, in a start (340 mm) and a finish (240 mm) variant, with concealed cable
  channels and pockets sized for the ADA2167 sensor body.
- Snap-in cable covers.
- A glue-on electronics housing with a display opening, USB opening, board supports, and a sliding lid — it holds the
  breakout board with the ESP32 display board plugged in.

Print the sensor pocket test part first whenever a sensor-related dimension changes.

## Why no level shifter is needed here

Earlier revisions of this document required four level shifters or optocouplers. With the ADA2167 that is **not**
needed: the receiver's white wire is an open-collector output. It only pulls the pin to GND and never drives a voltage
of its own, so the ESP32's internal 3.3 V pull-up defines the high level. The pin therefore never sees more than 3.3 V,
even when the sensor is powered from 5 V.

A level shifter or optocoupler is only required if you deviate from this list and use a sensor with a **push-pull 5 V
output**, or if you run long cables to external 12/24 V sensors. Details: [Wiring](WIRING.md).

## Wi-Fi without Internet

| Part | Qty. | Recommendation |
|---|---:|---|
| No additional equipment | – | Raspberry Pi access point for both ESP32 modules and one registration station |
| Optional travel router | 1 | More stable AP for event operation; LAN to Pi, local Wi-Fi without WAN |

## Optional RS485 fallback

| Part | Qty. | Note |
|---|---:|---|
| MAX3485 / 3.3 V RS485 transceiver | 2 | **Do not** connect a 5 V MAX485 directly to ESP32 GPIO |
| CAT5e/CAT6 cable | 1 | 10–20 m, communications cable only |
| 120-ohm terminating resistor | 2 | One A–B resistor at each bus end |
| 680-ohm bias resistors | 2–3 | At master/start as required by the transceiver; optional on a complete module |

RS485 was not part of the purchase above. On the display boards, the pins previously suggested for RS485 are partly
taken by the LCD; see the pin table in [Wiring](WIRING.md) before ordering.

## Future start release

| Part | Qty. | Note |
|---|---:|---|
| Servo or suitable solenoid | 2 | One physical gate per lane; build safely |
| Separate 5 V supply | 1 | 3–5 A for servos; share ground with start ESP |
| Driver/MOSFET + flyback diode | 2 | For solenoids only; never power from ESP32 |

## Sensor selection

Use **through-beam** sensors: transmitter and receiver face each other and the car blocks the beam. Reflective sensors
such as TCRT5000 are not the reference design because vehicle colour, sunlight, and distance affect them far more.

Each beam sensor here has two parts: a small transmitter emits invisible infrared light and a receiver sees it. The car
blocks the beam as it passes. This is more reliable than a sensor that merely waits for a reflected signal.

The ADA2167 meets the criteria that matter for this build: it runs from 3.3–5.5 V, its output is open collector and
therefore safe on a 3.3 V GPIO, it reacts in under 2 ms, and its rated range of about 25 cm covers a two-lane frame.
If you substitute another sensor, check the output type first — a 5 V push-pull output must never enter an ESP32 GPIO
without an interface.

---

**Navigation:** [← Project overview](PROJECT.md) · [Next: Architecture →](ARCHITECTURE.md) · [Deutsch](../de/MATERIALS.md)

**Shared resources:** [CAD package](../cad_files/README.md) · [ESP32 sensor firmware guide](../esp32/ESP32_SENSOR_FIRMWARE_GUIDE.md)

**All topics:** [Project overview](PROJECT.md) · **Materials** · [Architecture](ARCHITECTURE.md) · [Wiring](WIRING.md) · [Firmware](FIRMWARE.md) · [Protocol](PROTOCOL.md) · [Setup](SETUP.md) · [Testing](TESTING.md) · [ESP agent brief](ESP_AGENT_GUIDE.md) · [Racing Manager integration](AGENT_RACING_MANAGER_INTEGRATION.md)
