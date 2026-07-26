# Materials

Prices and seller listings change often, so this list specifies quantities and requirements instead of transient product links.

This step is only for purchasing and preparation. Do not buy parts blindly: with beam sensors, output voltage is particularly important. The list explains what each part is for and the minimum properties it needs.

## Required materials for one two-lane track

| Part | Qty. | Minimum requirement / purpose |
|---|---:|---|
| Raspberry Pi | 1 | Already owned; Wi-Fi, Racing Manager, web UI |
| Raspberry Pi power supply | 1 | Matched to Pi model and sized correctly |
| ESP32 DevKit | 2 | Standard ESP32, USB, at least six usable GPIOs; start and finish |
| IR through-beam sensors | 4 pairs | Transmitter + receiver, two lanes at start and finish |
| 3.3 V level shifter or optocoupler | 4 | **Required** if a receiver output is 5 V |
| 5 V start power supply | 1 | 2 A; 3–5 A after servos are added |
| 5 V finish power supply | 1 | 1–2 A |
| USB cables | 2 | Matching the ESP32 boards |
| Enclosures | 2 | Strain relief and touch/splash protection |
| Wire, terminals, Dupont leads | 1 set | Short internal connections |
| Sensor brackets | 8 | Rigid, adjustable; one per transmitter/receiver |
| Pi case / SD card | 1 | If not already available |

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

## Future start release

| Part | Qty. | Note |
|---|---:|---|
| Servo or suitable solenoid | 2 | One physical gate per lane; build safely |
| Separate 5 V supply | 1 | 3–5 A for servos; share ground with start ESP |
| Driver/MOSFET + flyback diode | 2 | For solenoids only; never power from ESP32 |

## Sensor selection

Use **through-beam** sensors: transmitter and receiver face each other and the car blocks the beam. Reflective sensors such as TCRT5000 are not the reference design because vehicle colour, sunlight, and distance affect them far more.

Each beam sensor here has two parts: a small transmitter emits invisible infrared light and a receiver sees it. The car blocks the beam as it passes. This is more reliable than a sensor that merely waits for a reflected signal.

Prefer sensors with a 3.3-V-compatible digital output. With a 5-V sensor, power and logic must be evaluated separately: a 5-V output must never enter an ESP32 GPIO. A level shifter, optocoupler, or properly engineered interface is mandatory.

---

**Navigation:** [← Project overview](PROJECT.md) · [Next: Architecture →](ARCHITECTURE.md) · [Deutsch](../de/MATERIALS.md)

**All topics:** [Project overview](PROJECT.md) · **Materials** · [Architecture](ARCHITECTURE.md) · [Wiring](WIRING.md) · [Firmware](FIRMWARE.md) · [Protocol](PROTOCOL.md) · [Setup](SETUP.md) · [Testing](TESTING.md) · [ESP agent brief](ESP_AGENT_GUIDE.md) · [Racing Manager integration](AGENT_RACING_MANAGER_INTEGRATION.md)
