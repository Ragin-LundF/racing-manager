# Pinout and wiring

You now connect parts electrically. Work with power supplies switched off, wire just one sensor first, and test it before adding another. A GPIO is a small ESP32 signal input; it may receive a 3.3-V signal only.

## Safety rules

> **Warning — 3.3 V:** ESP32 GPIO is not 5-V tolerant. A 5-V *push-pull* sensor output can destroy the input. Check every output with both its data sheet and a multimeter before connecting it.

> **Warning — power:** Sensors and ESP32 may use the same 5-V supply, but all logically connected devices require a shared ground. Future servos/solenoids need a separate, adequately sized supply.

## The sensor used: ADA2167

The [Adafruit ADA2167](https://www.amazon.de/dp/B01BU6YBWU) has three wires per part:

| Part | Wire colour | Function | Connection |
|---|---|---|---|
| IR transmitter | red | supply | 5 V / VIN |
| IR transmitter | black | ground | GND |
| IR receiver | red | supply | 5 V / VIN |
| IR receiver | black | ground | GND |
| IR receiver | white | open-collector signal | ESP32 sensor GPIO |

The white wire is an **open-collector output**: it only pulls the GPIO to GND and never drives a voltage of its own.
The ESP32's internal 3.3-V pull-up (`INPUT_PULLUP`) supplies the high level, so the pin never sees more than 3.3 V even
though the sensor is powered from 5 V. **No level shifter and no optocoupler are needed** for this sensor with short
in-frame wiring.

Do not add an external pull-up to 5 V on the white wire — that would put 5 V on the GPIO.

## Distribution inside a module

Power is distributed with the [WAGO 221-413](https://www.amazon.de/dp/B07NKCWBST) three-conductor lever connectors, one
for 5 V and one for GND per measuring module. Single sensor leads are extended with
[20 AWG silicone wire](https://www.amazon.de/dp/B0C7T9P8G7) and joined inline with
[LT-1 push-in connectors](https://www.amazon.de/dp/B0G6K9WJ2B).

```text
5 V WAGO:  ESP32 5V/VIN  ─┬─ transmitter red
                          └─ receiver red

GND WAGO:  ESP32 GND     ─┬─ transmitter black
                          └─ receiver black

Signal:    receiver white ─── sensor GPIO (internal pull-up enabled)
```

With two lanes per module, use one 5 V WAGO and one GND WAGO per lane, or a small chain of them. Each lane's white wire
goes to its own GPIO — never join the two signal wires.

The ESP32 board plugs into the [30-pin breakout board](https://www.amazon.de/dp/B0F6CLP43C), whose screw terminals take
the sensor wires without soldering. That assembly is what the printed [electronics housing](../cad_files/README.md) is
dimensioned for.

## Reference pinout

The pin map depends on the board. The reference build uses the ESP32 with the integrated 1.96" LCD, where the display
occupies several pins that older revisions of this document assigned to sensors.

| Module | Function | Plain ESP32 DevKit | Board with integrated LCD |
|---|---|---:|---:|
| START | Lane 1 start beam (white wire) | GPIO 32 | GPIO 16 |
| START | Lane 2 start beam (white wire) | GPIO 33 | GPIO 17 |
| FINISH | Lane 1 finish beam (white wire) | GPIO 32 | GPIO 16 |
| FINISH | Lane 2 finish beam (white wire) | GPIO 33 | GPIO 17 |
| both | RS485 RX | GPIO 16 | GPIO 25 |
| both | RS485 TX | GPIO 17 | GPIO 26 |
| both | RS485 DE + /RE | GPIO 4 | GPIO 27 |
| START | Future servo L1/L2 reserve | GPIO 25 / 26 | GPIO 13 / 14 |

On the display board, the LCD uses MOSI 23, SCLK 18, CS 15, DC 2, RST 4, and backlight 32. GPIO 32, 4, and 2 are
therefore unavailable for sensors or RS485 on that board, which is why the sensor inputs move to GPIO 16 and 17. The
firmware examples in the [ESP32 sensor firmware guide](../esp32/ESP32_SENSOR_FIRMWARE_GUIDE.md) use GPIO 16 for the
single documented sensor input.

On a plain DevKit, GPIO 32/33 are also usable as inputs and remain the recommended choice there.

This is a **reference pinout**, not an absolute rule. Verify the actual silkscreen and display pinout on the board you
received before wiring, and mirror any change in the firmware configuration ([Firmware](FIRMWARE.md)).

## When an interface *is* required

If you replace the ADA2167 with a sensor that has a push-pull 5-V output, or if you run long cables to external 12/24-V
sensors, use a bidirectional level shifter, an optocoupler, or an isolated digital input. A generic resistor divider is
not the reference event-hardware design.

## Optional RS485 bus

```text
ESP32 A                    CAT5e/CAT6                     ESP32 B
TX pin ─ DI  MAX3485                                  MAX3485  DI ─ TX pin
RX pin ─ RO  MAX3485                                  MAX3485  RO ─ RX pin
DE pin ─ DE,/RE MAX3485                              MAX3485 DE,/RE ─ DE pin
             A ═══════════════════════════════════════════ A
             B ═══════════════════════════════════════════ B
            GND ───────────────────────────────────────── GND (reference)
```

Fit 120 ohms across A–B at both bus ends. CAT cable is merely cable here; do not connect it to a network switch or PoE. Use a 3.3-V transceiver (MAX3485, SP3485, or equivalent), not an unprotected 5-V MAX485 board.

RS485 was not part of the reference purchase. On the display board it also competes with the LCD pins, so check the
table above before ordering transceivers.

---

**Navigation:** [← Architecture](ARCHITECTURE.md) · [Next: Firmware →](FIRMWARE.md) · [Deutsch](../de/WIRING.md)

**Shared resources:** [CAD package](../cad_files/README.md) · [ESP32 sensor firmware guide](../esp32/ESP32_SENSOR_FIRMWARE_GUIDE.md)

**All topics:** [Project overview](PROJECT.md) · [Materials](MATERIALS.md) · [Architecture](ARCHITECTURE.md) · **Wiring** · [Firmware](FIRMWARE.md) · [Protocol](PROTOCOL.md) · [Setup](SETUP.md) · [Testing](TESTING.md) · [ESP agent brief](ESP_AGENT_GUIDE.md) · [Racing Manager integration](AGENT_RACING_MANAGER_INTEGRATION.md)
